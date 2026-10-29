from pathlib import Path
import re,json,xml.etree.ElementTree as ET
repo_root=Path(__file__).resolve().parents[1]
base=repo_root.parent
repos=[repo_root.name]
brands=set('Shoal Swordfish Clownfish Blobfish Pufferfish Anglerfish YouTube TikTok Reddit Bluesky SponsorBlock DeArrow Liberapay Shizuku Shevery X TV HD SD 4K APK OAuth GitHub Sentry MaterialKolor NewPipeExtractor'.split())
def literals(s):
    i=0
    while i<len(s):
        if s.startswith('//',i):
            n=s.find('\n',i);i=n if n>=0 else len(s);continue
        if s.startswith('/*',i):
            n=s.find('*/',i+2);i=n+2 if n>=0 else len(s);continue
        if s.startswith('"""',i):
            n=s.find('"""',i+3);i=n+3 if n>=0 else len(s);continue
        if s[i]!='"':i+=1;continue
        i+=1;out='';param=0
        while i<len(s) and s[i]!='"':
            if s[i]=='\\' and i+1<len(s):
                out+= {'n':'\n','t':'\t','r':'\r'}.get(s[i+1],s[i+1]);i+=2;continue
            if s[i]=='$' and i+1<len(s) and s[i+1]=='{':
                i+=2;depth=1;quote=False
                while i<len(s) and depth:
                    c=s[i]
                    if c=='\\':i+=2;continue
                    if c=='"':quote=not quote
                    elif not quote:
                        if c=='{':depth+=1
                        elif c=='}':depth-=1
                    i+=1
                out+='{'+str(param)+'}';param+=1;continue
            if s[i]=='$' and i+1<len(s) and (s[i+1].isalpha() or s[i+1]=='_'):
                i+=2
                while i<len(s) and (s[i].isalnum() or s[i]=='_'):i+=1
                out+='{'+str(param)+'}';param+=1;continue
            out+=s[i];i+=1
        i+=1
        if len(out)>1 and out not in brands and re.search('[A-Za-z]{2}',out) and (out[0].isupper() or ' 'in out) and not any(x in out for x in ['https://','http://','application/','<script','<div','android.','androidx.','io.github.','{0}.{1}']):
            if '\\' not in out and '=' not in out and not out.startswith(('.', '/', '#','!')):yield out
allkeys=set()
for repo in repos:
    root=base/repo/'app/src/main';keys=set()
    for p in (root/'java').rglob('*.kt'):
        if p.name in ('AppLanguage.kt','LocalisedText.kt'):continue
        keys.update(literals(p.read_text()))
    notes=root/'assets/whats-new.json'
    if notes.exists():
        for note in json.loads(notes.read_text()):keys.update([note['title'],note['body']])
    keys.update(['App language','Follow system language','English (United Kingdom)','Choose app language','Some translations are automatic. Untranslated text appears in English.','Close','Cancel','Search languages'])
    out=root/'assets/i18n';out.mkdir(parents=True,exist_ok=True)
    (out/'en.json').write_text(json.dumps({k:k for k in sorted(keys)},ensure_ascii=False,indent=2)+'\n')
    allkeys.update(keys)
    print(repo,len(keys))

print('Unique:',len(allkeys))
