import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SKIP = {'build', '.gradle', '.git', 'run', 'runs', 'output', 'bettersearch-1.3.1', '__pycache__'}
LEXER = re.compile(r'(?P<string>"""[\s\S]*?"""|\'\'\'[\s\S]*?\'\'\'|"(?:\\[\s\S]|[^"\\])*"|\'(?:\\[\s\S]|[^\'\\])*\')|(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)|(?P<word>[\w$]+)|(?P<space>\s+)|(?P<symbol>.)')
OPTIONAL_ANNOTATION = re.compile(r'^\s*@(SuppressWarnings|SafeVarargs|Deprecated|FunctionalInterface)(?:\([^\n]*\))?\s*$', re.M)

def sources():
    for directory, dirs, files in os.walk(ROOT):
        dirs[:] = sorted(d for d in dirs if d not in SKIP)
        for filename in sorted(files):
            yield Path(directory) / filename

def tokens(text):
    return [match.group() for match in LEXER.finditer(text) if match.lastgroup not in ('comment', 'space')]

def strip(text, annotations):
    cleaned = ''.join(('\n' * match.group().count('\n') + ' ') if match.lastgroup == 'comment' else match.group() for match in LEXER.finditer(text))
    if annotations:
        cleaned = OPTIONAL_ANNOTATION.sub('', cleaned)
    expected = tokens(cleaned)
    cleaned = re.sub(r'\n{3,}', '\n\n', '\n'.join(line.rstrip() for line in cleaned.splitlines())).strip() + '\n'
    if tokens(cleaned) != expected:
        raise ValueError('Source token stream changed while removing whitespace')
    return cleaned

def object_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate JSON key: ' + key)
        result[key] = value
    return result

def audit(rewrite, jars):
    errors = []
    counts = {'java': 0, 'gradle': 0, 'json': 0, 'mixins': 0, 'languages': 0, 'jars': 0}
    files = list(sources())
    for path in files:
        if path.suffix in ('.java', '.gradle'):
            text = path.read_text(encoding='utf-8-sig')
            cleaned = strip(text, path.suffix == '.java')
            if rewrite and cleaned != text:
                path.write_text(cleaned, encoding='utf-8', newline='\n')
                text = cleaned
            if any(match.lastgroup == 'comment' for match in LEXER.finditer(text)):
                errors.append(str(path.relative_to(ROOT)) + ': comments remain')
            if path.suffix == '.java' and OPTIONAL_ANNOTATION.search(text):
                errors.append(str(path.relative_to(ROOT)) + ': nonfunctional annotation remains')
            if path.suffix == '.java' and 'src/main/java' in path.as_posix() and re.search(r'catch\s*\(\s*Throwable\b', text):
                errors.append(str(path.relative_to(ROOT)) + ': catches Throwable')
            counts[path.suffix[1:]] += 1
        if path.suffix == '.json' and 'src/main/resources' in path.as_posix():
            try:
                data = json.loads(path.read_text(encoding='utf-8-sig'), object_pairs_hook=object_pairs)
                counts['json'] += 1
                if path.name.endswith('mixins.json'):
                    package = data.get('package', '')
                    for name in data.get('client', []) + data.get('mixins', []):
                        relative = '/'.join(package.split('.')) + '/' + name.replace('.', '/') + '.java'
                        version = path.relative_to(ROOT).parts[1]
                        if not any(p.as_posix().endswith(relative) and version in p.parts for p in files):
                            errors.append(f'{path.relative_to(ROOT)}: missing mixin {name}')
                        counts['mixins'] += 1
            except (ValueError, TypeError) as error:
                errors.append(str(path.relative_to(ROOT)) + ': ' + str(error))
    for path in files:
        if path.name == 'en_us.json' and 'src/main/resources' in path.as_posix():
            expected = set(json.loads(path.read_text(encoding='utf-8-sig')))
            for language in path.parent.glob('*.json'):
                actual = set(json.loads(language.read_text(encoding='utf-8-sig')))
                missing = expected - actual
                extra = actual - expected
                if missing or extra:
                    errors.append(f'{language.relative_to(ROOT)}: language keys missing={sorted(missing)}, extra={sorted(extra)}')
                counts['languages'] += 1
        if path.name == 'en_US.lang' and 'src/main/resources' in path.as_posix():
            def lang_keys(file):
                return {line.split('=', 1)[0] for line in file.read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.startswith('#')}
            expected = lang_keys(path)
            for language in path.parent.glob('*.lang'):
                if lang_keys(language) != expected:
                    errors.append(str(language.relative_to(ROOT)) + ': inconsistent legacy language keys')
                counts['languages'] += 1
    props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8-sig').splitlines() if '=' in line and not line.startswith('#'))
    version = props['mod_version']
    constant = (ROOT / 'core/src/main/java/com/rivalzin/bettersearch/BetterSearch.java').read_text(encoding='utf-8-sig')
    if f'VERSION = "{version}"' not in constant:
        errors.append('Legacy version constant differs from Gradle metadata')
    for path in (ROOT / 'versions').glob('*/common/src/main/java/com/rivalzin/bettersearch/client/gui/LanguageSelectScreen.java'):
        text = path.read_text(encoding='utf-8-sig')
        if not re.search(r'(ModConfig\.apply|BetterSearchClient\.applyAndSave)\(settings\)', text):
            errors.append(str(path.relative_to(ROOT)) + ': language edits are not persisted on removal')
    if jars:
        artifacts = sorted((ROOT / 'build/dist').glob('*.jar'))
        targets = re.findall(r"'(versions:mc[^']+:(?:forge|neoforge|fabric))'", (ROOT / 'settings.gradle').read_text(encoding='utf-8-sig'))
        targets = sorted(set(targets))
        if len(artifacts) != len(targets):
            errors.append(f'Expected {len(targets)} jars, found {len(artifacts)}')
        for path in artifacts:
            if not path.name.endswith(f'-{version}.jar'):
                errors.append(path.name + ': obsolete version in dist')
            with zipfile.ZipFile(path) as archive:
                names = archive.namelist()
                if len(names) != len(set(names)):
                    errors.append(path.name + ': duplicate entries')
                for name in names:
                    if name.endswith('.class') and not name.startswith('com/rivalzin/bettersearch/'):
                        errors.append(path.name + ': foreign class ' + name)
                    if name.endswith('.class'):
                        major = int.from_bytes(archive.read(name)[6:8], 'big')
                        maximum = 52 if any('-' + v + '-' in path.name for v in ('1.7.10', '1.12.2', '1.16.5')) else 61 if any('-' + v + '-' in path.name for v in ('1.18.2', '1.19.2', '1.20.1')) else 69 if '-26.' in path.name else 65
                        if major > maximum:
                            errors.append(f'{path.name}: {name} class version {major} exceeds {maximum}')
                for required in ('async/AsyncIndexState.class', 'state/VersionedState.class', 'client/ConfigIo.class', 'FailurePolicy.class'):
                    if 'com/rivalzin/bettersearch/' + required not in names:
                        errors.append(path.name + ': missing ' + required)
                counts['jars'] += 1
    report = {'counts': counts, 'errors': errors, 'version': version}
    if jars:
        report['sha256'] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in artifacts}
    output = ROOT / 'build/audit/source-audit.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return bool(errors)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--strip', action='store_true')
    parser.add_argument('--jars', action='store_true')
    arguments = parser.parse_args()
    sys.exit(audit(arguments.strip, arguments.jars))
