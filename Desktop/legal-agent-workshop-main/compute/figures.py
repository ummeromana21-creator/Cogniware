#!/usr/bin/env python3
"""compute/figures.py - offline anatomy-style figures for the Fly Study page.

Runs headless sims (same engine as the lab), writes PNG + sidecar JSON beside
the page. The page renders them; nothing here runs in the browser.

Copy this directory into the Fly Brain folder, then e.g.:
    python3 -m compute.figures raster --out ../metaverse-lab/fig
    python3 -m compute.figures all --out ../metaverse-lab/fig

Every figure's label block: git commit, wiring dataset + cache hash, seed,
parameters, "steps" on the time axis, unitless dose, "simulated" in the title.
No numbers are typed in as expected results.
"""
import argparse
import datetime
import hashlib
import json
import os
import subprocess
import sys

import numpy as np

os.environ.setdefault('MPLCONFIGDIR', '/tmp/mpl')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))


def lab_path(args):
    if args.lab:
        return args.lab
    here_lab = os.path.dirname(HERE)
    try:
        sys.path.insert(0, here_lab)
        import cns_fly  # noqa: F401
        sys.path.remove(here_lab)
        return here_lab
    except ImportError:
        sys.exit('cannot find the lab: pass --lab "/Users/.../Fly Brain"')


def git_commit(lab):
    try:
        return subprocess.run(['git', '-C', lab, 'rev-parse', '--short', 'HEAD'],
                              capture_output=True, text=True, timeout=10).stdout.strip() or 'unknown'
    except Exception:
        return 'unknown'


def cache_hash(lab, subject):
    p = os.path.join(lab, 'data', subject, 'fly.npz')
    h = hashlib.sha1()
    with open(p, 'rb') as f:
        for blk in iter(lambda: f.read(1 << 20), b''):
            h.update(blk)
    return h.hexdigest()[:8]


def new_specimen(subject, seed, params=None):
    import cns_fly
    import fly
    import pharmacology as ph
    brain = cns_fly.CNSFly(subject, seed=seed, params=params)
    body = fly.FlyBody()
    world = fly.World(seed=seed)
    world.randomize_sugar()
    pharm = ph.Pharmacology(brain)
    return brain, body, world, pharm


def tick(brain, body, world, pharm, move=True):
    sensory, dist = world.get_sensory(body.x, body.y, body.angle)
    caught = dist < world.catch_radius
    if caught:
        world.randomize_sugar()
    motor, spiked = brain.step(sensory)
    pharm.step()
    if move:
        body.move(motor)
    return spiked, motor, caught


STAGE_ORDER = ['nose', 'antennal lobe PNs', 'lateral horn', 'mushroom body',
               'central complex', 'descending neurons', 'nerve cord', 'legs']


def stage_masks(brain):
    out = []
    for name in STAGE_ORDER:
        g = brain.groups.get(name)
        if g is not None and len(g['all']):
            out.append((name, np.asarray(g['all'])))
    return out


def savefig(fig, out, name, meta):
    png = os.path.join(out, name + '.png')
    fig.savefig(png, dpi=150, facecolor='#0b0d12')
    plt.close(fig)
    with open(os.path.join(out, name + '.json'), 'w') as f:
        json.dump(meta, f, indent=1)
    print('wrote', png)
    return png


def label(title, lab, subject, seed, params, xlabel='steps', dose='unitless', extra=None, dataset=None, seeds=None):
    if dataset is None:
        dataset = f'{subject} fly.npz sha1:{cache_hash(lab, subject)}'
    meta = {'title': title, 'simulated': True, 'git_commit': git_commit(lab),
            'dataset': dataset, 'date': datetime.date.today().isoformat(),
            'seed': seed, 'seeds': seeds or [seed], 'params': params, 'xlabel': xlabel, 'dose': dose}
    if extra:
        meta.update(extra)
    return meta


def style(ax, title, xlabel='steps'):
    ax.set_facecolor('#0b0d12')
    ax.set_title(title, color='#e8eaf0', fontsize=11)
    ax.set_xlabel(xlabel, color='#9aa0b2')
    ax.tick_params(colors='#9aa0b2')


# ------------------------------------------------------------------ raster
def fig_raster(a):
    brain, body, world, pharm = new_specimen(a.subject, a.seed, None)
    order = []
    bounds = []
    for name in ['sens_L', 'sens_R', 'central', 'motor_L', 'motor_R']:
        x, y = brain.regions[name]
        order.append(np.arange(x, y))
        bounds.append((name, len(order[-1])))
    order = np.concatenate(order)
    spikes_t, spikes_y = [], []
    for t in range(a.steps):
        sp, _, _ = tick(brain, body, world, pharm)
        idx = np.flatnonzero(sp[order])
        if idx.size:
            spikes_t.append(np.full(idx.size, t))
            spikes_y.append(idx)
    T = np.concatenate(spikes_t) if spikes_t else np.zeros(0)
    Y = np.concatenate(spikes_y) if spikes_y else np.zeros(0)
    if T.size > 150000:  # deterministic stride cap
        stride = int(np.ceil(T.size / 150000))
        T, Y = T[::stride], Y[::stride]
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.scatter(T, Y, s=0.4, c='#2ee6c8', alpha=0.7, linewidths=0)
    y = 0
    for name, n in bounds:
        y += n
        ax.axhline(y, color='#2a3048', linewidth=1)
        ax.text(a.steps + 6, y - n / 2, name, color='#9aa0b2', fontsize=8, va='center')
    ax.set_xlim(0, a.steps)
    ax.set_ylim(0, order.size)
    ax.set_ylabel('neuron (sorted by stage)', color='#9aa0b2')
    style(ax, f'Baseline raster, simulated {a.subject} (seed {a.seed})')
    return savefig(fig, a.out, f'raster-{a.subject}', label(
        ax.get_title(), a.lab, a.subject, a.seed, {'I_bg': brain.params['I_bg'], 'noise': brain.params['noise']}))


# ------------------------------------------------------------------ rates
def fig_rates(a):
    brain, body, world, pharm = new_specimen(a.subject, a.seed, None)
    masks = stage_masks(brain)
    sched = [('baseline', 200, None), ('dose caffeine 0.5 (unitless)', 400, ('dose', 'caffeine', 0.5)),
             ('washout', 400, ('washout',))]
    traces = {name: [] for name, _ in masks}
    bands = []
    t = 0
    for phase, n, act in sched:
        if act and act[0] == 'dose':
            pharm.dose(act[1], act[2])
        elif act and act[0] == 'washout':
            pharm.washout()
        bands.append((phase, t, t + n))
        for _ in range(n):
            sp, _, _ = tick(brain, body, world, pharm)
            for name, idx in masks:
                traces[name].append(float(sp[idx].mean()))
            t += 1
    fig, axes = plt.subplots(len(masks), 1, figsize=(10, 2 + len(masks)), sharex=True)
    if len(masks) == 1:
        axes = [axes]
    for ax, (name, _) in zip(axes, masks):
        ax.plot(traces[name], color='#2ee6c8', linewidth=0.8)
        for phase, x0, x1 in bands:
            if phase.startswith('dose'):
                ax.axvspan(x0, x1, color='#ffd23f', alpha=0.12)
            elif phase == 'washout':
                ax.axvspan(x0, x1, color='#5aa9ff', alpha=0.08)
        ax.set_ylabel(name[:14], color='#9aa0b2', fontsize=8)
        ax.set_facecolor('#0b0d12')
        ax.tick_params(colors='#9aa0b2')
    style(axes[0], f'Population rates per stage, simulated {a.subject} (seed {a.seed})')
    axes[-1].set_xlabel('steps', color='#9aa0b2')
    fig.tight_layout()
    return savefig(fig, a.out, f'rates-{a.subject}', label(
        axes[0].get_title(), a.lab, a.subject, a.seed,
        {'I_bg': brain.params['I_bg'], 'noise': brain.params['noise']},
        extra={'bands': [b[0] for b in bands]}))

# --------------------------------------------------------------- waterfall
# Stage tests mirror stage_map.STAGES (the P7 diagnostic): banana ORNs, PNs,
# lateral horn, KCs, MBONs, LAL, central complex, descending neurons, VNC.
def stage_map_masks(brain):
    import cns_fly
    m = brain.meta
    pt = np.asarray(m['primary_type']).astype(str)
    sc = np.asarray(m['super_class']).astype(str)
    side = np.asarray(m['side']).astype(str)
    glom = list(cns_fly.BANANA_GLOMERULI)
    import re as _re
    tests = [
        ('banana ORNs', np.array([any(t.startswith('ORN_' + g) or t.startswith(g) for g in glom) for t in pt])),
        ('all uPNs', np.array([bool(_re.match(r'^(D|V|VA|VC|VL|VM|DA|DC|DL|DM|DP)[A-Z]*\d+[a-z]?_(ad|l|il|v|lv)PN$', t)) for t in pt])),
        ('lateral horn', np.char.startswith(pt, 'LH')),
        ('mushroom KCs', np.char.startswith(pt, 'KC')),
        ('MBONs', np.char.startswith(pt, 'MBON')),
        ('LAL', np.char.startswith(pt, 'LAL')),
        ('central complex', np.array([t.startswith(('EPG', 'PEN', 'PFL', 'FC', 'hDelta', 'vDelta', 'ER', 'Delta7', 'FR')) for t in pt])),
        ('descending', np.array(['descending' in x for x in sc])),
        ('nerve cord', np.array(['vnc_intrinsic' in x or 'ventral_nerve_cord' in x for x in sc])),
    ]
    out = []
    for name, mk in tests:
        mk = np.asarray(mk, dtype=bool)
        if mk.sum() > 5:
            out.append((name, np.flatnonzero(mk & (side == 'left')), np.flatnonzero(mk & (side == 'right'))))
    return out


def fig_waterfall(a):
    masks = None
    wins = 12
    li_runs = []
    for bx in (3.0, -3.0):  # banana left (+y), then right; fly held still at origin heading +x
        brain, body, world, pharm = new_specimen(a.subject, a.seed + (0 if bx < 0 else 100), None)
        if masks is None:
            masks = stage_map_masks(brain)
        body.x, body.y, body.angle = 0.0, 0.0, 0.0
        world.sugar_x, world.sugar_y = 0.0, bx
        per = [[] for _ in masks]
        for t in range(a.steps):
            sp, _, _ = tick(brain, body, world, pharm, move=False)
            for i, (_, L, R) in enumerate(masks):
                l = float(sp[L].mean()) if L.size else 0.0
                r = float(sp[R].mean()) if R.size else 0.0
                ipsi, contra = (l, r) if bx > 0 else (r, l)
                per[i].append((ipsi - contra) / (ipsi + contra + 1e-9))
        w = a.steps // wins
        li_runs.append(np.array([[np.mean(col[i * w:(i + 1) * w]) for i in range(wins)] for col in per]))
    li = np.mean(li_runs, axis=0)
    fig, ax = plt.subplots(figsize=(10, 5))
    im = ax.imshow(li, aspect='auto', cmap='RdYlGn', vmin=-1, vmax=1,
                   extent=[0, a.steps, len(masks), 0])
    ax.set_yticks(np.arange(len(masks)) + 0.5)
    ax.set_yticklabels([m[0] for m in masks], color='#e8eaf0', fontsize=9)
    fig.colorbar(im, ax=ax, label='LI (banana side − far side)/(sum)')
    style(ax, f'Smell-signal waterfall (P7), simulated {a.subject} (seed {a.seed})')
    return savefig(fig, a.out, f'waterfall-{a.subject}', label(
        ax.get_title(), a.lab, a.subject, a.seed, {'I_bg': brain.params['I_bg'], 'noise': brain.params['noise']},
        extra={'stages': [m[0] for m in masks], 'seeds': [a.seed, a.seed + 100]}))


# ------------------------------------------------------------------ regime
def fig_regime(a):
    ibgs = np.linspace(0.5, 2.5, 9)
    noises = np.linspace(0.1, 2.5, 9)
    firing = np.zeros((9, 9))
    sync = np.zeros((9, 9))
    for i, ib in enumerate(ibgs):
        for j, nz in enumerate(noises):
            brain, body, world, pharm = new_specimen(a.subject, a.seed, {'I_bg': float(ib), 'noise': float(nz)})
            fr = []
            for t in range(230):
                sp, _, _ = tick(brain, body, world, pharm)
                if t >= 30:
                    fr.append(float(sp.mean()))
            med = float(np.median(fr)) if np.median(fr) > 0 else 1e-9
            firing[i, j] = float(np.mean(fr))
            sync[i, j] = float(np.mean([f > 2 * med for f in fr]))
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.5))
    brain0, _, _, _ = new_specimen(a.subject, a.seed, None)
    d0 = (brain0.params['I_bg'], brain0.params['noise'])
    for ax, mat, ttl in zip(axes, [firing, sync], ['mean firing (fraction)', 'burstiness (frac steps >2x median)']):
        im = ax.imshow(mat, origin='lower', aspect='auto', cmap='magma',
                       extent=[noises[0], noises[-1], ibgs[0], ibgs[-1]])
        ax.plot([d0[1]], [d0[0]], marker='*', color='#2ee6c8', markersize=14, label='lab default')
        ax.legend(fontsize=8)
        fig.colorbar(im, ax=ax)
        ax.set_xlabel('noise (unitless)', color='#9aa0b2')
        ax.set_ylabel('I_bg (unitless)', color='#9aa0b2')
        ax.set_title(ttl, color='#e8eaf0', fontsize=10)
        ax.set_facecolor('#0b0d12')
        ax.tick_params(colors='#9aa0b2')
    fig.suptitle(f'Regime map, simulated {a.subject} (seed {a.seed})', color='#e8eaf0')
    fig.tight_layout()
    bi = int(np.argmin([abs(x - d0[0]) for x in ibgs]))
    bj = int(np.argmin([abs(x - d0[1]) for x in noises]))
    res = {'firing_min': round(float(firing.min()), 4), 'firing_max': round(float(firing.max()), 4),
           'firing_at_default': round(float(firing[bi, bj]), 4), 'burst_at_default': round(float(sync[bi, bj]), 4)}
    return savefig(fig, a.out, f'regime-{a.subject}', label(
        f'Regime map, simulated {a.subject} (seed {a.seed})', a.lab, a.subject, a.seed,
        {'I_bg_grid': [float(ibgs[0]), float(ibgs[-1])], 'noise_grid': [float(noises[0]), float(noises[-1])],
         'default': list(d0)}, xlabel='noise (unitless)', extra={'results': res}))


# ------------------------------------------------------------------ dose
DOSE_SUBSTANCES = ['caffeine', 'ethanol', 'isoflurane']
DOSES = [0.0, 0.25, 0.5, 1.0, 2.0]


def fig_dose(a):
    sexes = ['banc', 'malecns']
    fig, axes = plt.subplots(len(DOSE_SUBSTANCES), 2, figsize=(11, 9), sharex=True)
    allmeans = {}
    allpts = {}
    for i, drug in enumerate(DOSE_SUBSTANCES):
        for j, sex in enumerate(sexes):
            ax = axes[i][j]
            means = []
            ptslist = []
            for d in DOSES:
                pts = []
                for rep in range(3):
                    brain, body, world, pharm = new_specimen(sex, a.seed + rep, None)
                    for _ in range(30):
                        tick(brain, body, world, pharm)
                    if d > 0:
                        pharm.dose(drug, d)
                    fr = []
                    for _ in range(a.steps):
                        sp, _, _ = tick(brain, body, world, pharm)
                        fr.append(float(sp.mean()))
                    pts.append(float(np.mean(fr)))
                means.append(float(np.mean(pts)))
                ptslist.append([float(v) for v in pts])
                ax.scatter([d] * len(pts), pts, s=18, color='#5aa9ff', alpha=0.8, zorder=3)
            ax.plot(DOSES, means, color='#ffd23f', linewidth=1.6)
            allmeans[(drug, sex)] = means
            allpts[(drug, sex)] = ptslist
            ax.set_title(f'{drug}, simulated {sex}', color='#e8eaf0', fontsize=10)
            ax.set_facecolor('#0b0d12')
            ax.tick_params(colors='#9aa0b2')
            if i == len(DOSE_SUBSTANCES) - 1:
                ax.set_xlabel('dose (unitless)', color='#9aa0b2')
            if j == 0:
                ax.set_ylabel('firing (fraction)', color='#9aa0b2')
    results = {}
    for drug in DOSE_SUBSTANCES:
        results[drug] = {}
        for sex in sexes:
            ms = allmeans[(drug, sex)]
            results[drug][sex] = round((ms[-1] - ms[0]) / ms[0] * 100, 1) if ms[0] else 0.0
    import experiments as EX
    dose_preds = {'caffeine': 'higher', 'ethanol': 'lower', 'isoflurane': 'lower'}
    verdicts = {}
    for drug in DOSE_SUBSTANCES:
        verdicts[drug] = {}
        for sex in sexes:
            mini = {'measure': 'firing_all', 'prediction': dose_preds[drug],
                    'control': {'name': 'no drug'}, 'treatments': [{'name': 'full dose'}]}
            runs = {'no drug': [{'firing_all': v} for v in allpts[(drug, sex)][0]],
                    'full dose': [{'firing_all': v} for v in allpts[(drug, sex)][-1]]}
            txt, v, _det = EX.analyse_one(mini, runs)
            verdicts[drug][sex] = {'verdict': v, 'analysis': txt, 'prediction': dose_preds[drug]}
    fig.suptitle(f'Dose–response per substance per sex, simulated (seed {a.seed})', color='#e8eaf0')
    fig.tight_layout()
    grid = savefig(fig, a.out, 'dose-response', label(
        f'Dose–response per substance per sex, simulated (seed {a.seed})', a.lab, 'banc', a.seed,
        {'substances': DOSE_SUBSTANCES, 'doses': DOSES, 'repeats': 3}, xlabel='dose (unitless)',
        dataset=f"banc sha1:{cache_hash(a.lab, 'banc')}+malecns sha1:{cache_hash(a.lab, 'malecns')}",
        extra={'seeds': [a.seed, a.seed + 1, a.seed + 2], 'results': results, 'verdicts': verdicts}))
    for drug in DOSE_SUBSTANCES:
        df, daxes = plt.subplots(1, 2, figsize=(11, 4), sharex=True, sharey=True)
        for j, sex in enumerate(sexes):
            ax = daxes[j]
            for d, pts in zip(DOSES, allpts[(drug, sex)]):
                ax.scatter([d] * len(pts), pts, s=22, color='#5aa9ff', alpha=0.8, zorder=3)
            ax.plot(DOSES, allmeans[(drug, sex)], color='#ffd23f', linewidth=1.6)
            ax.set_title(f'{drug}, simulated {sex}', color='#e8eaf0', fontsize=11)
            ax.set_xlabel('dose (unitless)', color='#9aa0b2')
            ax.set_facecolor('#0b0d12')
            ax.tick_params(colors='#9aa0b2')
        daxes[0].set_ylabel('firing (fraction)', color='#9aa0b2')
        df.suptitle(f'{drug} dose–response per sex, simulated (seed {a.seed})', color='#e8eaf0')
        df.tight_layout()
        savefig(df, a.out, f'dose-{drug}', label(
            f'{drug} dose–response per sex, simulated (seed {a.seed})', a.lab, 'banc', a.seed,
            {'substance': drug, 'doses': DOSES, 'repeats': 3}, xlabel='dose (unitless)',
            dataset=f"banc sha1:{cache_hash(a.lab, 'banc')}+malecns sha1:{cache_hash(a.lab, 'malecns')}",
            extra={'seeds': [a.seed, a.seed + 1, a.seed + 2],
                   'results': {drug: results[drug]}, 'verdicts': {drug: verdicts[drug]}}))
    if a.recout:
        os.makedirs(a.recout, exist_ok=True)
        rec = {'id': f'exp-dose-rec-{datetime.date.today().isoformat()}', 'date': datetime.date.today().isoformat(),
               'seeds': [a.seed, a.seed + 1, a.seed + 2], 'simulated': True,
               'substances': DOSE_SUBSTANCES, 'doses': DOSES,
               'runs': {drug: {sex: {str(d): allpts[(drug, sex)][k] for k, d in enumerate(DOSES)}
                               for sex in sexes} for drug in DOSE_SUBSTANCES},
               'verdicts': verdicts, 'predictions': dose_preds,
               'label': {'git_commit': git_commit(a.lab),
                         'dataset': f"banc sha1:{cache_hash(a.lab, 'banc')}+malecns sha1:{cache_hash(a.lab, 'malecns')}"}}
        with open(os.path.join(a.recout, 'rec-dose.json'), 'w') as f:
            json.dump(rec, f, indent=1)
        print('wrote', os.path.join(a.recout, 'rec-dose.json'))
    return grid

# ------------------------------------------------------------- timecourse
def fig_timecourse(a):
    slots = [('control', 'banc'), ('control', 'malecns'), ('treatment', 'banc'), ('treatment', 'malecns')]
    sched = [('before', 200, None), ('during (caffeine 0.5, unitless)', 400, True), ('washout', 400, False)]
    win = 25
    reps = {}
    seeds = []
    for rep in range(3):
        for si, (box, sex) in enumerate(slots):
            seed = a.seed + rep * 10 + si
            seeds.append(seed)
            brain, body, world, pharm = new_specimen(sex, seed, None)
            vals = []
            for phase, n, drug in sched:
                if drug is True and box == 'treatment':
                    pharm.dose('caffeine', 0.5)
                elif drug is False and box == 'treatment':
                    pharm.washout()
                acc = []
                for t in range(n):
                    sp, _, _ = tick(brain, body, world, pharm)
                    acc.append(float(sp.mean()))
                    if len(acc) == win:
                        vals.append(float(np.mean(acc)))
                        acc = []
            reps[(rep, box, sex)] = vals
    nwin = len(next(iter(reps.values())))
    xs = np.arange(nwin) * win
    fig, axes = plt.subplots(2, 2, figsize=(11, 7), sharex=True, sharey=True)
    bounds = [0, 200, 600, 1000]
    for ax, key in zip(axes.flat, slots):
        box, sex = key
        for rep in range(3):
            ax.plot(xs, reps[(rep, box, sex)], color='#5aa9ff', linewidth=0.7, alpha=0.45)
        mean = [float(np.mean([reps[(rep, box, sex)][k] for rep in range(3)])) for k in range(nwin)]
        ax.plot(xs, mean, color='#2ee6c8', linewidth=1.4)
        ax.axvspan(bounds[1], bounds[2], color='#ffd23f', alpha=0.12)
        ax.axvspan(bounds[2], bounds[3], color='#5aa9ff', alpha=0.08)
        ax.set_title(f'{box}:{sex}, simulated (mean of 3, faint lines are runs)', color='#e8eaf0', fontsize=9)
        ax.set_facecolor('#0b0d12')
        ax.tick_params(colors='#9aa0b2')
    for ax in axes[1]:
        ax.set_xlabel('steps', color='#9aa0b2')
    for ax in axes[:, 0]:
        ax.set_ylabel('firing (fraction)', color='#9aa0b2')
    fig.suptitle(f'Time course before/during/washout per fly per box, simulated (3 repeats, base seed {a.seed})', color='#e8eaf0')
    fig.tight_layout()
    d0, d1 = bounds[1] // win, bounds[2] // win
    import experiments as EX
    verdicts = {}
    for sex in ('banc', 'malecns'):
        ctrl = [float(np.mean(reps[(r, 'control', sex)][d0:d1])) for r in range(3)]
        treat = [float(np.mean(reps[(r, 'treatment', sex)][d0:d1])) for r in range(3)]
        mini = {'measure': 'firing_all', 'prediction': 'higher',
                'control': {'name': 'control box'}, 'treatments': [{'name': 'dosed box'}]}
        runs = {'control box': [{'firing_all': v} for v in ctrl],
                'dosed box': [{'firing_all': v} for v in treat]}
        txt, v, _det = EX.analyse_one(mini, runs)
        verdicts[sex] = {'verdict': v, 'analysis': txt, 'control': ctrl, 'treatment': treat}
    out = savefig(fig, a.out, 'timecourse', label(
        f'Time course before/during/washout per fly per box, simulated (3 repeats, base seed {a.seed})', a.lab,
        'banc', a.seed, {'drug': 'caffeine 0.5', 'window': win, 'repeats': 3},
        dataset=f"banc sha1:{cache_hash(a.lab, 'banc')}+malecns sha1:{cache_hash(a.lab, 'malecns')}",
        seeds=seeds, extra={'verdicts': verdicts}))
    if a.recout:
        os.makedirs(a.recout, exist_ok=True)
        rec = {'id': f'exp-drugs-rec-{datetime.date.today().isoformat()}', 'date': datetime.date.today().isoformat(),
               'seeds': seeds, 'simulated': True, 'drug': 'caffeine 0.5', 'window': win,
               'traces': {f'{box}:{sex}/run{r + 1}': reps[(r, box, sex)]
                          for r in range(3) for box, sex in slots},
               'verdicts': verdicts, 'prediction': 'higher',
               'label': {'git_commit': git_commit(a.lab),
                         'dataset': f"banc sha1:{cache_hash(a.lab, 'banc')}+malecns sha1:{cache_hash(a.lab, 'malecns')}"}}
        with open(os.path.join(a.recout, 'rec-drugs.json'), 'w') as f:
            json.dump(rec, f, indent=1)
        print('wrote', os.path.join(a.recout, 'rec-drugs.json'))
    return out


# ------------------------------------------------------------------ vtrace
def fig_vtrace(a):
    brain, body, world, pharm = new_specimen(a.subject, a.seed, None)
    c0, c1 = brain.regions['central']
    counts = np.zeros(c1 - c0)
    for _ in range(200):  # screen for a central neuron that actually fires
        sp, _, _ = tick(brain, body, world, pharm)
        counts += sp[c0:c1]
    idx = c0 + int(np.argmax(counts))
    v = np.zeros(a.steps, dtype=np.float32)
    spk = np.zeros(a.steps, dtype=bool)
    for t in range(a.steps):
        sp, _, _ = tick(brain, body, world, pharm)
        v[t] = brain.v[idx]
        spk[t] = sp[idx]
    fig, ax = plt.subplots(figsize=(10, 4))
    ax.plot(v, color='#5aa9ff', linewidth=0.9)
    # Dots sit on the voltage that crossed threshold: post-step v already holds
    # the reset plus fresh synaptic input, so use the previous step's value.
    st = np.flatnonzero(spk)
    sy = np.where(st > 0, v[np.maximum(st - 1, 0)], brain.params['v_thresh'])
    ax.scatter(st, sy, s=10, color='#ffd23f', zorder=3)
    ax.axhline(brain.params['v_thresh'], color='#ff6b6b', linestyle='--', linewidth=1)
    ax.text(8, brain.params['v_thresh'] + 0.6, 'threshold', color='#ff6b6b', fontsize=9)
    ax.set_ylabel('mV', color='#9aa0b2')
    style(ax, f'Single-neuron voltage trace, simulated {a.subject} (seed {a.seed})')
    return savefig(fig, a.out, f'vtrace-{a.subject}', label(
        ax.get_title(), a.lab, a.subject, a.seed,
        {'I_bg': brain.params['I_bg'], 'noise': brain.params['noise'], 'neuron': int(idx)},
        extra={'ylabel': 'mV', 'results': {'n_spikes': int(spk.sum())}}))


# --------------------------------------------------------------- ringbump
def fig_ringbump(a):
    brain, body, world, pharm = new_specimen(a.subject, a.seed, None)
    pt = np.asarray(brain.meta['primary_type']).astype(str)
    epg = np.flatnonzero(np.char.startswith(pt, 'EPG'))
    if epg.size == 0:
        sys.exit(f'no EPG neurons in {a.subject}')
    H = np.zeros((epg.size, a.steps), dtype=np.float32)
    for t in range(a.steps):
        sp, _, _ = tick(brain, body, world, pharm)
        H[:, t] = sp[epg].astype(np.float32)
    sm = np.zeros_like(H)
    w = 5
    for t in range(a.steps):
        sm[:, t] = H[:, max(0, t - w + 1):t + 1].mean(axis=1)
    fig, ax = plt.subplots(figsize=(10, 5))
    ax.imshow(sm, aspect='auto', cmap='magma', extent=[0, a.steps, epg.size, 0])
    ax.set_ylabel('EPG index (index order, not anatomy)', color='#9aa0b2')
    style(ax, f'Ring-bump over EPG, simulated {a.subject} (seed {a.seed})')
    return savefig(fig, a.out, f'ringbump-{a.subject}', label(
        ax.get_title(), a.lab, a.subject, a.seed,
        {'I_bg': brain.params['I_bg'], 'noise': brain.params['noise'], 'n_epg': int(epg.size)}))


# ------------------------------------------------------------------ CLI
FIGS = {'raster': fig_raster, 'rates': fig_rates, 'waterfall': fig_waterfall,
        'regime': fig_regime, 'dose': fig_dose, 'timecourse': fig_timecourse,
        'vtrace': fig_vtrace, 'ringbump': fig_ringbump}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('fig', choices=sorted(FIGS) + ['all', 'must'])
    ap.add_argument('--subject', default='banc')
    ap.add_argument('--seed', type=int, default=11)
    ap.add_argument('--steps', type=int, default=400)
    ap.add_argument('--out', default='compute/out')
    ap.add_argument('--recout', default=None)
    ap.add_argument('--lab', default=None)
    a = ap.parse_args()
    a.lab = lab_path(a)
    sys.path.insert(0, a.lab)
    os.makedirs(a.out, exist_ok=True)
    if a.fig == 'all':
        names = ['raster', 'rates', 'waterfall', 'regime', 'dose', 'timecourse', 'vtrace', 'ringbump']
    elif a.fig == 'must':
        names = ['raster', 'rates']
    else:
        names = [a.fig]
    for n in names:
        FIGS[n](a)


if __name__ == '__main__':
    main()

