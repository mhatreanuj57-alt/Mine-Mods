from glb import *
import json, numpy as np
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'tools' / 'source_models' / '3_final_transport_golem_3x.glb'
assets = root / 'src' / 'main' / 'resources' / 'assets' / 'transportgolem'
j,b=load(source)
nodes=j['nodes']
def r(x,n=4): return [round(float(v),n) for v in x]
out={'unitsPerBlock':60.0,'nodes':[],'animations':{}}
for i,n in enumerate(nodes):
    d={'name':n['name'],'t':r(n.get('translation',[0,0,0]),4),'r':r(n.get('rotation',[0,0,0,1]),6),'children':n.get('children',[]),'quads':[]}
    if 'mesh' in n:
        p=j['meshes'][n['mesh']]['primitives'][0]
        pos=acc(j,b,p['attributes']['POSITION']);nrm=acc(j,b,p['attributes']['NORMAL']);uv=acc(j,b,p['attributes']['TEXCOORD_0']);idx=acc(j,b,p['indices']).flatten()
        assert len(pos)%4==0
        for f in range(len(pos)//4):
            tris=[t for t in idx.reshape(-1,3).tolist() if t[0]//4==f]
            assert len(tris)==2
            t1,t2=tris; shared=[v for v in t1 if v in t2]; assert len(shared)==2
            u1=[v for v in t1 if v not in shared][0];u2=[v for v in t2 if v not in shared][0]
            order=[u1,shared[0],u2,shared[1]]
            q=[]
            for v in order:
                q+= r(pos[v],3)+r(nrm[v],3)+r(uv[v],5)
            d['quads'].append(q)
    out['nodes'].append(d)
assert {'Root', 'Torso_Pivot', 'Elbow_Left', 'Elbow_Right', 'Driver_Seat_Cushion',
        'Passenger_Seat_1_Left_Cushion', 'Passenger_Seat_3_Right_Cushion'} <= {n['name'] for n in nodes}
for a in j['animations']:
    ch=[];dur=0
    for c in a['channels']:
        s=a['samplers'][c['sampler']]
        t=acc(j,b,s['input']).flatten();v=acc(j,b,s['output'])
        dur=max(dur,float(t.max()))
        ch.append({'node':c['target']['node'],'path':'t' if c['target']['path']=='translation' else 'r','times':r(t,4),'values':[r(x,5) for x in v]})
    out['animations'][a['name']]={'duration':round(dur,4),'channels':ch}
assert {'walk', 'ground_smash'} <= out['animations'].keys()
model_path = assets / 'model' / 'transport_golem.json'
model_path.parent.mkdir(parents=True, exist_ok=True)
model_path.write_text(json.dumps(out, separators=(',',':')), encoding='utf-8')
image = j['images'][0]
view = j['bufferViews'][image['bufferView']]
start = view.get('byteOffset', 0)
texture_path = assets / 'textures' / 'entity' / 'transport_golem.png'
texture_path.parent.mkdir(parents=True, exist_ok=True)
texture_path.write_bytes(b[start:start + view['byteLength']])
print(f'Wrote {model_path.stat().st_size} bytes of model data and {texture_path.stat().st_size} bytes of texture data')
