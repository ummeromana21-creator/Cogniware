// Handles: signup, login, logout, get session
const https = require('https');

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;

function supabaseRequest(path, method, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(SUPABASE_URL + path);
    const data = JSON.stringify(body);
    const headers = {
      'Content-Type': 'application/json',
      'apikey': SUPABASE_ANON_KEY,
      'Content-Length': Buffer.byteLength(data)
    };
    if (token) headers['Authorization'] = 'Bearer ' + token;

    const req = https.request({
      hostname: url.hostname,
      path: url.pathname + url.search,
      method,
      headers
    }, (res) => {
      let d = '';
      res.on('data', c => d += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(d) }); }
        catch(e) { resolve({ status: res.statusCode, data: d }); }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

exports.handler = async function(event) {
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 200, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'POST, OPTIONS' }, body: '' };
  }

  const { action, email, password, token } = JSON.parse(event.body || '{}');

  try {
    let result;

    if (action === 'signup') {
      result = await supabaseRequest('/auth/v1/signup', 'POST', { email, password });
    } else if (action === 'login') {
      result = await supabaseRequest('/auth/v1/token?grant_type=password', 'POST', { email, password });
    } else if (action === 'logout') {
      result = await supabaseRequest('/auth/v1/logout', 'POST', {}, token);
    } else if (action === 'user') {
      result = await supabaseRequest('/auth/v1/user', 'GET', {}, token);
    } else {
      return { statusCode: 400, body: JSON.stringify({ error: 'Unknown action' }) };
    }

    return {
      statusCode: 200,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      body: JSON.stringify(result.data)
    };
  } catch (err) {
    return { statusCode: 500, headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ error: err.message }) };
  }
};
