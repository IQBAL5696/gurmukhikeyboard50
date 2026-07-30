import re
import os

path = 'app/src/main/assets/gurbani/vaara_bhai_gurdas/content.txt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

def is_p(t):
    if not t: return False
    t = t.strip()
    if '(' in t or ')' in t or "'" in t: return True
    # Placeholder vowels pattern \u0a72 is ੲ, \u0a73 is ੳ, \u0a05 is ਅ
    if re.search(r'[ਕ-ਹ][\u0a72\u0a73\u0a05]', t): return True
    if t.endswith('ਙ') or t.endswith('ਙਙ'): return True
    return False

cleaned = []
for line in lines:
    l = line.strip()
    if not l or l == '': continue
    if re.search(r'੍ਹੋਮੲ|ਵਾਰਾਂ|ਪੰਨਾ|ਫਰੲਵੋਿੁਸ|ਂੲਣਟ|੍ਹੲਲਪ|ਛੋਪੇਰਗਿਹਟ|Copyright|ਵ\d+\.\d+|Trademark|Foundation', l):
        continue
    if re.search(r'[a-zA-Z]', l): continue

    m = re.match(r'^(ਪਉੜੀ\s*)?(\d+)\s*:(.*)', l)
    if m:
        title = m.group(3).strip()
        if is_p(title): continue
        num = m.group(2)
        cleaned.append('')
        cleaned.append(f"ਪਉੜੀ {num} : {title}")
        continue

    if re.search(r'[।॥]', l):
        if is_p(l): continue
        cleaned.append(l)

final = []
for i, line in enumerate(cleaned):
    if line == 'ੴ ਸਤਿਗੁਰ ਪ੍ਰਸਾਦਿ॥' and i > 0 and final and final[-1] == 'ੴ ਸਤਿਗੁਰ ਪ੍ਰਸਾਦਿ॥':
        continue
    final.append(line)

result = '\n'.join(final).strip()
result = re.sub(r'\n{3,}', '\n\n', result)

print(result)
