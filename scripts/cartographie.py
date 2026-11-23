#!/usr/bin/env python3
"""Cartographie du depot : modules, dependances, paquets, tailles et tests, dans .breakdown/.

Usage (depuis la racine du depot) : python3 scripts/cartographie.py
Ecrit .breakdown/cartographie.html (page autonome, theme clair et sombre) et .breakdown/spec.json (flux du salon
Discord : blocs de code et appels, pour un outil de visualisation)."""
import html, json, os, re
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, '.breakdown')


def modules():
    parent = open(os.path.join(ROOT, 'pom.xml'), encoding='utf-8').read()
    return re.findall(r'<module>([^<]+)</module>', parent)


def deps(module):
    pom = open(os.path.join(ROOT, module, 'pom.xml'), encoding='utf-8').read()
    body = pom.split('<dependencies>', 1)[1] if '<dependencies>' in pom else ''
    own = re.search(r'<artifactId>([^<]+)</artifactId>', pom.split('</parent>', 1)[-1]).group(1)
    return sorted({d for d in re.findall(r'<artifactId>(novabank-[\w-]+)</artifactId>', body) if d != own})


def sources(module, kind):
    out = []
    for dp, _, files in os.walk(os.path.join(ROOT, module, 'src', kind, 'java')):
        out += [os.path.join(dp, f) for f in files if f.endswith('.java') and f != 'package-info.java']
    return out


def lines(path):
    return sum(1 for line in open(path, encoding='utf-8') if line.strip())


def package(path):
    m = re.search(r'^package dev\.novadev\.novabank\.([\w.]+);', open(path, encoding='utf-8').read(), re.M)
    return m.group(1) if m else '?'


def stats():
    out = {}
    for m in modules():
        main = sources(m, 'main')
        tests = [p for p in sources(m, 'test') if p.endswith('Test.java')]
        packages = defaultdict(lambda: [0, 0])
        for p in main:
            packages[package(p)][0] += 1
            packages[package(p)][1] += lines(p)
        out[m] = dict(deps=[d for d in deps(m) if d in modules()], classes=len(main),
                      lines=sum(lines(p) for p in main), tests=len(tests), packages=dict(sorted(packages.items())))
    return out


def layers(data):
    """Rang = 1 + rang maximal des dependances : les modules de base en haut."""
    rank = {}

    def of(m):
        if m not in rank:
            rank[m] = 1 + max((of(d) for d in data[m]['deps']), default=-1)
        return rank[m]
    for m in data:
        of(m)
    rows = defaultdict(list)
    for m, r in rank.items():
        rows[r].append(m)
    return [sorted(rows[r]) for r in sorted(rows)]


def svg(data):
    rows = layers(data)
    width, row_h, box_w, box_h = 1100, 110, 170, 56
    pos = {}
    for r, row in enumerate(rows):
        for i, m in enumerate(row):
            pos[m] = ((i + 1) * width / (len(row) + 1), 40 + r * row_h)
    parts = ['<svg viewBox="0 0 %d %d" width="100%%" role="img" aria-label="Dependances entre modules">'
             % (width, 40 + len(rows) * row_h),
             '<defs><marker id="a" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">'
             '<path d="M0,0 L8,4 L0,8 z" fill="currentColor"/></marker></defs>']
    for m, (x, y) in pos.items():
        for d in data[m]['deps']:
            dx, dy = pos[d]
            parts.append('<line x1="%.0f" y1="%d" x2="%.0f" y2="%d" stroke="currentColor" stroke-opacity=".25" '
                         'marker-end="url(#a)"/>' % (x, y, dx, dy + box_h))
    for m, (x, y) in pos.items():
        s = data[m]
        parts.append('<g><rect x="%.0f" y="%d" width="%d" height="%d" rx="8" fill="var(--box)" stroke="currentColor" '
                     'stroke-opacity=".3"/><text x="%.0f" y="%d" text-anchor="middle" font-weight="600" '
                     'font-size="13">%s</text><text x="%.0f" y="%d" text-anchor="middle" font-size="11" opacity=".75">'
                     '%d classes, %d lignes, %d tests</text></g>'
                     % (x - box_w / 2, y, box_w, box_h, x, y + 22, m.replace('novabank-', ''), x, y + 41,
                        s['classes'], s['lines'], s['tests']))
    parts.append('</svg>')
    return ''.join(parts)


def page(data):
    total = [sum(s[k] for s in data.values()) for k in ('classes', 'lines', 'tests')]
    out = ['<!doctype html><html lang="fr"><meta charset="utf-8">'
           '<meta name="viewport" content="width=device-width,initial-scale=1"><title>Cartographie NovaBank</title>'
           '<style>:root{--bg:#fff;--fg:#1c1c1e;--box:#eef2ff}'
           '@media(prefers-color-scheme:dark){:root{--bg:#1c1c1e;--fg:#eee;--box:#2b2f45}}'
           'body{background:var(--bg);color:var(--fg);font:15px system-ui;max-width:1100px;margin:0 auto;padding:16px}'
           'svg{color:var(--fg)}table{border-collapse:collapse;margin:8px 0 16px}'
           'td,th{padding:3px 14px 3px 0;text-align:left}details{margin:6px 0}</style>',
           '<h1>Cartographie NovaBank</h1>',
           '<p>%d modules, %d classes, %d lignes de code (hors lignes vides), %d classes de test. Une fleche part '
           'd\'un module vers celui dont il depend ; les modules de base sont en haut.</p>'
           % (len(data), total[0], total[1], total[2]), svg(data), '<h2>Paquets par module</h2>']
    for m, s in data.items():
        out.append('<details><summary><b>%s</b> (%d classes, depend de : %s)</summary><table><tr><th>Paquet</th>'
                   '<th>Classes</th><th>Lignes</th></tr>' % (m, s['classes'], ', '.join(s['deps']) or 'rien'))
        for p, (c, n) in s['packages'].items():
            out.append('<tr><td><code>%s</code></td><td>%d</td><td>%d</td></tr>' % (html.escape(p), c, n))
        out.append('</table></details>')
    return '\n'.join(out) + '\n'


def block(path, name, label=None, parent=None, role=None):
    """Bloc de code : la declaration de {name} jusqu'a son accolade fermante."""
    text = open(os.path.join(ROOT, path), encoding='utf-8').read().split('\n')
    short = name.split('.')[-1]
    declaration = re.compile(r'^\s*(?:(?:public|private|protected|static|final)\s+)*'
                             r'(?:(?:class|record|interface)\s+' + re.escape(short) + r'\b'
                             r'|[A-Za-z][\w<>,.? \[\]]*\s' + re.escape(short) + r'\s*\()')
    start = next(i for i, l in enumerate(text) if declaration.search(l) and not l.rstrip().endswith(';'))
    depth, end = 0, start
    for i in range(start, len(text)):
        depth += text[i].count('{') - text[i].count('}')
        if depth == 0 and '{' in ''.join(text[start:i + 1]):
            end = i
            break
    out = {'id': name, 'file': path, 'start': start + 1, 'end': end + 1}
    for k, v in (('parent', parent), ('role', role), ('label', label)):
        if v:
            out[k] = v
    return out


def spec():
    base = 'novabank-bukkit/src/main/java/dev/novadev/novabank/bukkit/'
    feed, text, sender = base + 'discord/DiscordFeed.java', base + 'discord/DiscordText.java', base + 'discord/DiscordSender.java'
    blocks = [block(feed, 'DiscordFeed', role='container'),
              block(feed, 'DiscordFeed.onOperation', 'Entry point', 'DiscordFeed'),
              block(feed, 'DiscordFeed.flush', 'Entry point (timer)', 'DiscordFeed'),
              block(feed, 'DiscordFeed.test', 'Entry point (/novabank discord test)', 'DiscordFeed'),
              block(feed, 'DiscordFeed.announced', 'Filter', 'DiscordFeed'),
              block(feed, 'DiscordFeed.send', 'HTTP + retry', 'DiscordFeed'),
              block(text, 'DiscordText.group', 'Groups per player'),
              block(text, 'DiscordText.payloads', 'Embeds per message'),
              block(sender, 'DiscordSender.send', 'Webhook or bot'),
              block(base + 'command/DiscordCommand.java', 'DiscordCommand', 'Admin subcommand', role='type')]
    edges = [('DiscordFeed.onOperation', 'DiscordFeed.announced'), ('DiscordFeed.flush', 'DiscordText.group'),
             ('DiscordFeed.flush', 'DiscordText.payloads'), ('DiscordFeed.flush', 'DiscordFeed.send'),
             ('DiscordFeed.send', 'DiscordSender.send'), ('DiscordCommand', 'DiscordFeed.test'),
             ('DiscordFeed.test', 'DiscordSender.send')]
    return {'kind': 'flow', 'title': 'Salon Discord', 'entry': ['DiscordFeed.onOperation', 'DiscordFeed.flush',
                                                                 'DiscordFeed.test'],
            'blocks': blocks, 'edges': [{'from': a, 'to': b} for a, b in edges]}


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    data = stats()
    with open(os.path.join(OUT, 'cartographie.html'), 'w', encoding='utf-8') as f:
        f.write(page(data))
    with open(os.path.join(OUT, 'spec.json'), 'w', encoding='utf-8') as f:
        json.dump(spec(), f, ensure_ascii=False, indent=2)
    print('%d modules, %d classes, %d tests -> %s' % (len(data), sum(s['classes'] for s in data.values()),
                                                     sum(s['tests'] for s in data.values()), OUT))
