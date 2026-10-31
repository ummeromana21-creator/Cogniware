"""card_compiler.py - compile an ask-box question into a run card or an
explanation, using Muse Spark, then validated against knowledge.json.

The AI writes the card; it never writes numbers. Every factual claim in the
reply must trace to knowledge.json (filed papers, mechanisms) or the
reference cache (retrieved datasets). Anything else is dropped and noted.
Flies only: no human or medical advice, ever.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

SYSTEM = """You compile questions for a virtual fruit-fly neuroscience lab into JSON cards.
Reply with ONE JSON object only, no other text. Shapes:
run: {"mode":"run","substance":<name>,"measure":<key>,"hypothesis":<1 sentence>,"prediction":"higher"|"lower"|"different","procedure":[<steps>],"expected_from":[<quotes>],"caveat":<string or null>}
explain: {"mode":"explain","title":<string>,"paragraphs":[<plain sentences, one idea each>],"stages":[<sim stage names shown>],"refs":[<quotes>]}
refuse: {"mode":"refuse","reason":<1 sentence>}
Rules: substance must be one of the DOSABLE list; measure one of the MEASURES list.
Use "explain" for anatomy, mechanism, pharmacokinetics, toxicology and why-questions.
Use "refuse" for untestable topics and for anything about humans, medicine or dosing people.
"you", one idea per sentence, everyday pictures for technical words.
expected_from and refs must QUOTE the filed evidence or cached records below; never invent papers.
The subjects are simulated flies (Maxime female, Hagrid male)."""


def default_knowledge_path():
    return os.path.join(HERE, 'metaverse-lab', 'ask', 'knowledge.json')


def default_ref_dir():
    return os.path.join(HERE, 'reference_cache')


def load_knowledge(path=None):
    with open(path or default_knowledge_path()) as f:
        return json.load(f)


def ref_lines(substance, ref_dir):
    """Short quotable lines from the cached record, each tagged with source."""
    import references
    rec = references.lookup(substance, ref_dir or default_ref_dir())
    if not rec:
        return []
    out = []
    try:
        props = rec['records']['pubchem']['parts']['compound']['data']['PropertyTable']['Properties'][0]
        out.append(f"[pubchem] {props.get('Title')}: {props.get('MolecularFormula')} / {props.get('IUPACName')}")
    except (KeyError, IndexError, TypeError):
        pass
    try:
        descs = rec['records']['pubchem']['parts']['description']['data']['InformationList']['Information']
        for d in descs[:2]:
            for v in (d.get('Description') or [])[:1]:
                out.append(f"[pubchem] {v}"[:400])
    except (KeyError, IndexError, TypeError):
        pass
    return out


STAGE1 = """You route questions for a virtual fruit-fly neuroscience lab.
Reply with ONE JSON object only: {"mode":"run"|"explain"|"refuse","substance":<name or null>,"measure":<key or null>,"ref_substances":[<names>]}.
run = a testable drug effect on the flies. explain = anatomy, mechanism, pharmacokinetics, toxicology, why-questions. refuse = untestable topics, or anything about humans, medicine or dosing people.
Maxime is a female simulated fly and Hagrid is a male simulated fly — questions about them are fly questions, never refuse them as human.
substance and ref_substances from DOSABLE only. measure from MEASURES only."""

STAGE2_RUN = """You write one run card for a virtual fruit-fly lab. Reply with ONE JSON object only: {"substance":<name>,"measure":<key>,"hypothesis":<1 sentence>,"prediction":"higher"|"lower"|"different","procedure":[<steps>],"expected_from":[<quotes>],"caveat":<string or null>}.
"you", one idea per sentence, everyday pictures for technical words.
expected_from must QUOTE the filed evidence or cached records below; never invent papers.
The subjects are simulated flies (Maxime female, Hagrid male)."""

STAGE2_EXPLAIN = """You write one explainer for a virtual fruit-fly lab. Reply with ONE JSON object only: {"title":<string>,"paragraphs":[<plain sentences, one idea each>],"stages":[<stage names>],"refs":[<quotes>]}.
"you", one idea per sentence, everyday pictures for technical words.
refs must QUOTE the filed evidence or cached records below; never invent papers.
The subjects are simulated flies (Maxime female, Hagrid male)."""


def stage1_prompt(question, knowledge):
    dosable = [n for n, s in knowledge['substances'].items() if s.get('dosable')]
    return STAGE1, '\n'.join([
        f'Question: {question}', '',
        'DOSABLE: ' + ', '.join(dosable),
        'MEASURES: ' + ', '.join(knowledge['measures']),
        'UNTESTABLE: ' + '; '.join('/'.join(u['keys']) for u in knowledge.get('untestable', []))])


def build_prompt(question, knowledge, substance=None, ref_dir=None):
    """Stage-2 run prompt for one substance (short by design)."""
    s = knowledge['substances'][substance]
    user = [f'Question: {question}', '',
            f'SUBSTANCE: {substance} ({s.get("kind")}). Mechanism: {s.get("mechanism")}.',
            'DEFAULT: measure %s, prediction %s.' % (s['expect']['measure'], s['expect']['direction']),
            'WHY: ' + s['expect']['why'],
            'MEASURES: ' + ', '.join(f'{k} ({v})' for k, v in knowledge['measures'].items()),
            '', 'FILED EVIDENCE (quote these):'] + list(s.get('evidence', []) or ['(none filed)'])
    cached = ref_lines(substance, ref_dir)
    if cached:
        user += ['', 'CACHED RECORDS (quote with [pubchem] tag):'] + cached
    return STAGE2_RUN, '\n'.join(user)


def explain_prompt(question, knowledge, ref_substances, ref_dir):
    ev = []
    for n in ref_substances or []:
        s = knowledge['substances'].get(n)
        if s:
            ev += [f'{n}: {e}' for e in s.get('evidence', [])]
            ev += ref_lines(n, ref_dir)
    if not ev:
        for n, s in knowledge['substances'].items():
            ev += [f'{n}: {e}' for e in s.get('evidence', [])]
        ev = ev[:6]
    return STAGE2_EXPLAIN, '\n'.join(
        [f'Question: {question}', '', 'STAGES: ' + ', '.join(knowledge.get('stages', [])),
         '', 'FILED EVIDENCE (quote these):'] + ev)


def call_llm(system, user):
    try:
        import agent as _a
        _a.load_env()
    except Exception:
        pass
    key = os.environ.get('META_API_KEY', '')
    if not key or key.startswith('your_'):
        raise RuntimeError('no AI key (set META_API_KEY)')
    try:
        from openai import OpenAI
    except ImportError:
        raise RuntimeError("the 'openai' package is missing")
    client = OpenAI(api_key=key, base_url=os.environ.get('META_BASE_URL', 'https://api.meta.ai/v1'),
                    timeout=60)
    r = client.chat.completions.create(model=os.environ.get('META_MODEL', 'muse-spark-1.3'),
                                       max_tokens=8000, temperature=0.2,
                                       reasoning_effort='low',
                                       response_format={'type': 'json_object'},
                                       messages=[{'role': 'system', 'content': system},
                                                 {'role': 'user', 'content': user}])
    return r.choices[0].message.content


def parse_reply(text):
    if not text or not isinstance(text, str):
        raise ValueError('empty reply from AI')
    start, end = text.find('{'), text.rfind('}')
    if start < 0 or end <= start:
        raise ValueError('no JSON object in reply')
    return json.loads(text[start:end + 1])


def resolve_substance(name, knowledge):
    q = (name or '').lower().strip()
    for n, s in knowledge['substances'].items():
        if q == n.lower() or q in [a.lower() for a in s.get('aliases', [])]:
            return n, s
    return None, None


def _grounded(quotes, allowed):
    """Keep quotes that overlap a filed line; drop the rest. Returns (kept, dropped)."""
    kept, dropped = [], []
    flat = [a.lower() for a in allowed]
    for q in quotes or []:
        words = [w for w in q.lower().split() if len(w) > 4]
        hit = any(sum(1 for w in words if w in a) >= 3 for a in flat) if words else False
        (kept if hit else dropped).append(q)
    return kept, dropped


def validate_run(spec, knowledge, ref_dir):
    notes = []
    name, s = resolve_substance(spec.get('substance'), knowledge)
    if not s:
        return None, [f"unknown substance '{spec.get('substance')}'"]
    if not s.get('dosable'):
        return None, [f'{name} is not dosable: {s.get("not_modelled")}']
    measure = spec.get('measure')
    if measure not in knowledge['measures']:
        notes.append(f"unknown measure '{measure}', using {s['expect']['measure']}")
        measure = s['expect']['measure']
    pred = spec.get('prediction')
    if pred not in ('higher', 'lower', 'different'):
        notes.append(f"unknown prediction '{pred}', using {s['expect']['direction']}")
        pred = s['expect']['direction']
    allowed = list(s.get('evidence', [])) + ref_lines(name, ref_dir)
    kept, dropped = _grounded(spec.get('expected_from'), allowed)
    if dropped:
        notes.append(f'dropped {len(dropped)} unfiled citation(s)')
    card = {'title': f'{name} vs control ({measure})',
            'hypothesis': spec.get('hypothesis') or f'{name} changes {knowledge["measures"][measure]}.',
            'control': {'name': 'control box', 'setup': [{'cmd': 'restore'}]},
            'treatments': [{'name': f'{name} 0.5',
                            'setup': [{'cmd': 'restore'}, {'cmd': 'dose', 'drug': name, 'amount': 0.5}]}],
            'measure': measure, 'prediction': pred, 'repeats': 3, 'steps': 400, 'author': 'ask-ai',
            'procedure': spec.get('procedure') or []}
    return {'mode': 'run', 'substance': name, 'measure': measure, 'card': card,
            'expect': {'direction': pred, 'why': s['expect']['why']},
            'evidence': kept, 'caveat': spec.get('caveat') or s.get('not_modelled'),
            'confidence': s.get('confidence'), 'mechanism': s.get('mechanism'),
            'kind': s.get('kind'), 'notes': notes}, []


def validate_explain(spec, knowledge, ref_dir):
    stages = [st for st in (spec.get('stages') or []) if st in knowledge.get('stages', [])]
    allowed = [e for n, s in knowledge['substances'].items() for e in s.get('evidence', [])]
    name, _ = resolve_substance(spec.get('substance') or '', knowledge)
    if name:
        allowed += ref_lines(name, ref_dir)
    kept, dropped = _grounded(spec.get('refs'), allowed)
    notes = [f'dropped {len(dropped)} unfiled citation(s)'] if dropped else []
    return {'mode': 'explain', 'title': spec.get('title') or 'Explainer',
            'paragraphs': spec.get('paragraphs') or [], 'stages': stages,
            'refs': kept, 'notes': notes}, []


def compile_question(question, knowledge=None, ref_dir=None, llm_call=None):
    """Full pipeline for the compile_card verb. Raises on failure (page falls back)."""
    knowledge = knowledge or load_knowledge()
    ref_dir = ref_dir or default_ref_dir()
    q = (question or '').strip()
    if not q:
        raise ValueError('empty question')
    ask = llm_call or call_llm
    system, user = stage1_prompt(q, knowledge)
    route, mode = None, None
    for _ in range(2):  # one retry: the router occasionally refuses valid questions
        route = parse_reply(ask(system, user))
        mode = route.get('mode')
        if mode in ('run', 'explain'):
            break
    if mode == 'refuse':
        raise ValueError(route.get('reason') or 'not testable')
    if mode == 'run':
        name, s = resolve_substance(route.get('substance'), knowledge)
        if not s:
            raise ValueError(f"unknown substance '{route.get('substance')}'")
        if not s.get('dosable'):
            raise ValueError(f'{name} is not dosable: {s.get("not_modelled")}')
        system, user = build_prompt(q, knowledge, name, ref_dir)
        spec = parse_reply(ask(system, user))
        spec.setdefault('substance', name)
        if route.get('measure'):
            spec.setdefault('measure', route.get('measure'))
        card, problems = validate_run(spec, knowledge, ref_dir)
    elif mode == 'explain':
        system, user = explain_prompt(q, knowledge, route.get('ref_substances'), ref_dir)
        spec = parse_reply(ask(system, user))
        card, problems = validate_explain(spec, knowledge, ref_dir)
    else:
        raise ValueError(f"unknown mode '{mode}'")
    if problems:
        raise ValueError('; '.join(problems))
    card['badge'] = 'ai'
    card['question'] = q
    return card
