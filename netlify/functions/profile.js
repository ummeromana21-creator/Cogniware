// Handles: save profile, get profile, save assessment, get assessments
const https = require('https');

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;

function supabaseRequest(path, method, body, token) {
  return new Promise((resolve, reject) => {
    const url = new URL(SUPABASE_URL + path);
    const data = body ? JSON.stringify(body) : '';
    const headers = {
      'Content-Type': 'application/json',
      'apikey': SUPABASE_ANON_KEY,
      'Authorization': 'Bearer ' + token,
      'Prefer': 'return=representation'
    };
    if (data) headers['Content-Length'] = Buffer.byteLength(data);

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
    if (data) req.write(data);
    req.end();
  });
}

exports.handler = async function(event) {
  if (event.httpMethod === 'OPTIONS') {
    return { statusCode: 200, headers: { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type, Authorization', 'Access-Control-Allow-Methods': 'POST, GET, OPTIONS' }, body: '' };
  }

  const token = (event.headers.authorization || '').replace('Bearer ', '');
  if (!token) return { statusCode: 401, body: JSON.stringify({ error: 'No token' }) };

  const { action, data } = JSON.parse(event.body || '{}');

  try {
    let result;

    if (action === 'get_profile') {
      result = await supabaseRequest('/rest/v1/profiles?select=*', 'GET', null, token);
      return { statusCode: 200, headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify(result.data[0] || null) };
    }

    if (action === 'save_profile') {
      // Upsert profile
      result = await supabaseRequest('/rest/v1/profiles?on_conflict=id', 'POST', data, token);
      return { statusCode: 200, headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify(result.data) };
    }

    if (action === 'save_assessment') {
      result = await supabaseRequest('/rest/v1/assessments', 'POST', data, token);
      return { statusCode: 200, headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify(result.data) };
    }

    if (action === 'get_assessments') {
      result = await supabaseRequest('/rest/v1/assessments?select=*&order=created_at.desc&limit=10', 'GET', null, token);
      return { statusCode: 200, headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify(result.data) };
    }

    return { statusCode: 400, body: JSON.stringify({ error: 'Unknown action' }) };

  } catch (err) {
    return { statusCode: 500, headers: { 'Access-Control-Allow-Origin': '*' }, body: JSON.stringify({ error: err.message }) };
  }
};
