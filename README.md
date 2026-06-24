# Cogniware — Deployment Guide

## Deploy to Netlify in 3 steps

### Step 1 — Upload to Netlify
1. Go to [app.netlify.com](https://app.netlify.com)
2. Click **Add new site → Deploy manually**
3. Drag the entire `cogniware-deploy` folder onto the page
4. Netlify will give you a URL like `https://your-site.netlify.app`

### Step 2 — Add your API key
1. In Netlify, go to **Site configuration → Environment variables**
2. Click **Add a variable**
3. Key: `ANTHROPIC_API_KEY`
4. Value: your `sk-ant-...` key
5. Click **Save**
6. Go to **Deploys → Trigger deploy → Deploy site** to restart with the key

### Step 3 — Done
Anyone with the URL can now use the full assessment. Your API key stays on the server — users never see it.

## How it works
- `index.html` — the full Cogniware assessment app
- `netlify/functions/interpret.js` — a serverless function that receives the assessment data from the browser and calls the Anthropic API using your server-side key
- On Netlify, the key input card is hidden automatically — users just take the assessment
- Locally (file:// or localhost), the app falls back to asking for a key directly

## Costs
Each assessment calls Claude Sonnet once, generating ~4000 tokens. At current Anthropic pricing this is under $0.02 per assessment.

## Custom domain
In Netlify: **Site configuration → Domain management → Add a domain**.
