import json, struct, sys, math
from pathlib import Path

GLB = Path(sys.argv[1])
OUT = Path(sys.argv[2]); OUT.mkdir(parents=True, exist_ok=True)

data = GLB.read_bytes()
magic, ver, total_len = struct.unpack_from('<III', data, 0)
assert magic == 0x46546C67, 'not a GLB'
off = 12; js = None; bin_ = b''
chunks = []
while off < total_len:
    clen, ctype = struct.unpack_from('<II', data, off); off += 8
    chunk = data[off:off+clen]; off += clen
    chunks.append((hex(ctype), clen))
    if ctype == 0x4E4F534A: js = json.loads(chunk.decode('utf-8'))
    elif ctype == 0x004E4942: bin_ = chunk

out = []
def P(*a): out.append(' '.join(str(x) for x in a))

CT_STR = {5120:'BYTE',5121:'UBYTE',5122:'SHORT',5123:'USHORT',5125:'UINT',5126:'FLOAT'}
CT_SIZE = {5120:1,5121:1,5122:2,5123:2,5125:4,5126:4}
CT_FMT = {5120:'b',5121:'B',5122:'h',5123:'H',5125:'I',5126:'f'}
NUM = {'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT2':4,'MAT3':9,'MAT4':16}

def read_acc(i):
    acc = js['accessors'][i]
    if 'bufferView' not in acc: return None
    bv = js['bufferViews'][acc['bufferView']]
    base = bv.get('byteOffset',0) + acc.get('byteOffset',0)
    comps = NUM[acc['type']]; bpe = CT_SIZE[acc['componentType']]
    fmt = CT_FMT[acc['componentType']]
    stride = bv.get('byteStride') or comps*bpe
    vals = []
    for k in range(acc['count']):
        vals.extend(struct.unpack_from('<'+fmt*comps, bin_, base + k*stride))
    return vals

asset = js.get('asset',{})
P('== GLB HEADER ==')
P('file_size_bytes:', len(data), ' declared_length:', total_len, ' version:', ver)
P('generator:', asset.get('generator'), '| version:', asset.get('version'))
P('chunks:', chunks)
P('extensionsUsed:', js.get('extensionsUsed'))
P('extensionsRequired:', js.get('extensionsRequired'))

# --- images / textures ---
P()
P('== TEXTURES / IMAGES ==')
imgdir = OUT/'textures'; imgdir.mkdir(exist_ok=True)
def img_dims(raw):
    if raw[:8] == b'\x89PNG\r\n\x1a\n':
        w, h = struct.unpack('>II', raw[16:24]); return 'PNG', w, h
    if raw[:2] == b'\xff\xd8':
        i = 2
        while i < len(raw)-9:
            if raw[i] != 0xFF: i += 1; continue
            m = raw[i+1]
            if m in (0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF):
                h, w = struct.unpack('>HH', raw[i+5:i+9]); return 'JPEG', w, h
            if m in (0xD8,0xD9) or 0xD0 <= m <= 0xD7: i += 2; continue
            seglen = struct.unpack('>H', raw[i+2:i+4])[0]; i += 2 + seglen
        return 'JPEG', '?', '?'
    if raw[:4] == b'RIFF' and raw[8:12] == b'WEBP': return 'WEBP', '?', '?'
    return 'UNKNOWN', '?', '?'
for idx, img in enumerate(js.get('images', [])):
    if 'uri' in img:
        P(f'image[{idx}] uri={img["uri"]} mime={img.get("mime_type")}')
        continue
    bv = js['bufferViews'][img['bufferView']]
    base = bv.get('byteOffset',0); ln = bv['byteLength']
    raw = bin_[base:base+ln]
    kind, w, h = img_dims(raw)
    ext = {'PNG':'png','JPEG':'jpg','WEBP':'webp'}.get(kind,'bin')
    fn = imgdir/f'image{idx}_{w}x{h}.{ext}'
    fn.write_bytes(raw)
    P(f'image[{idx}] embedded {kind} {w}x{h} bytes={ln} sampler={None} -> {fn.name}')
for i, tex in enumerate(js.get('textures', [])):
    P(f'texture[{i}] source={tex.get("source")} sampler={tex.get("sampler")} name={tex.get("name")}')
if 'samplers' in js:
    for i, s in enumerate(js['samplers']):
        P(f'sampler[{i}] mag={s.get("magFilter")} min={s.get("minFilter")} wrapS={s.get("wrapS")} wrapT={s.get("wrapT")} (nearest={s.get("magFilter")==9728})')

# --- materials ---
P()
P('== MATERIALS ==')
for i, m in enumerate(js.get('materials', [])):
    pbr = m.get('pbrMetallicRoughness', {})
    bits = []
    if 'baseColorTexture' in pbr: bits.append(f'baseColorTex={pbr["baseColorTexture"]["index"]}')
    if 'baseColorFactor' in pbr: bits.append(f'baseColorFactor={pbr["baseColorFactor"]}')
    bits.append(f'metallic={pbr.get("metallicFactor",1.0)} roughness={pbr.get("roughnessFactor",1.0)}')
    if 'metallicRoughnessTexture' in pbr: bits.append(f'metalRoughTex={pbr["metallicRoughnessTexture"]["index"]}')
    if 'normalTexture' in m: bits.append(f'normalTex={m["normalTexture"]["index"]}')
    if 'occlusionTexture' in m: bits.append(f'occlusionTex={m["occlusionTexture"]["index"]}')
    if 'emissiveTexture' in m: bits.append(f'emissiveTex={m["emissiveTexture"]["index"]}')
    bits.append(f'emissive={m.get("emissiveFactor")}')
    bits.append(f'alphaMode={m.get("alphaMode","OPAQUE")} cutoff={m.get("alphaCutoff")} doubleSided={m.get("doubleSided",False)}')
    if m.get('extensions'): bits.append(f'ext={list(m["extensions"].keys())}')
    P(f'material[{i}] "{m.get("name","")}" ' + ' '.join(bits))

# --- nodes / scenes ---
nodes = js.get('nodes', [])
P()
P('== NODES / HIERARCHY ==')
P('node_count:', len(nodes))
children_of = {}
parent_of = {}
for i, n in enumerate(nodes):
    for c in n.get('children', []):
        children_of.setdefault(i, []).append(c); parent_of[c] = i
roots = [i for i in range(len(nodes)) if i not in parent_of]
def node_label(i):
    n = nodes[i]
    s = f'{i}:"{n.get("name","")}"'
    if 'mesh' in n: s += f'[mesh {n["mesh"]}]'
    if 'skin' in n: s += f'[skin {n["skin"]}]'
    if 'translation' in n: s += f' t={n["translation"]}'
    if 'scale' in n: s += f' s={n["scale"]}'
    if 'rotation' in n: s += f' r={n["rotation"]}'
    if 'matrix' in n: s += ' [matrix]'
    return s
def walk(i, depth=0):
    P('  '*depth + node_label(i))
    for c in children_of.get(i, []): walk(c, depth+1)
for r in roots: walk(r)
P('animated_nodes:')
anim_nodes = set()
for a in js.get('animations', []):
    for ch in a.get('channels', []):
        t = ch.get('target', {})
        if 'node' in t: anim_nodes.add(t['node'])
for i in sorted(anim_nodes):
    P('  ' + node_label(i))
P('skinned_nodes:', [node_label(i) for i,n in enumerate(nodes) if 'skin' in n])
for si, sk in enumerate(js.get('skins', [])):
    P(f'skin[{si}] joints={len(sk.get("joints",[]))} skeleton={sk.get("skeleton")} inverseBindMatrices={sk.get("inverseBindMatrices")}')

# world-space bbox (TRS hierarchy, column-major mat4)
def mat_identity(): return [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]
def mat_mul(a, b):
    r = [0.0]*16
    for c in range(4):
        for rr in range(4):
            r[c*4+rr] = sum(a[k*4+rr]*b[c*4+k] for k in range(4))
    return r
def trs_matrix(n):
    if 'matrix' in n: return list(n['matrix'])
    t = n.get('translation',[0,0,0]); r = n.get('rotation',[0,0,0,1]); s = n.get('scale',[1,1,1])
    x,y,z,w = r
    rot = [
        1-2*(y*y+z*z), 2*(x*y+z*w), 2*(x*z-y*w), 0,
        2*(x*y-z*w), 1-2*(x*x+z*z), 2*(y*z+x*w), 0,
        2*(x*z+y*w), 2*(y*z-x*w), 1-2*(x*x+y*y), 0,
        0,0,0,1]
    scale = [s[0],0,0,0, 0,s[1],0,0, 0,0,s[2],0, 0,0,0,1]
    trans = [1,0,0,0, 0,1,0,0, 0,0,1,0, t[0],t[1],t[2],1]
    return mat_mul(mat_mul(trans, rot), scale)
def apply(m, v):
    x,y,z = v
    return (m[0]*x+m[4]*y+m[8]*z+m[12], m[1]*x+m[5]*y+m[9]*z+m[13], m[2]*x+m[6]*y+m[10]*z+m[14])
world = {}
def walkm(i, parent):
    w = mat_mul(parent, trs_matrix(nodes[i])); world[i] = w
    for c in children_of.get(i, []): walkm(c, w)
for r in roots: walkm(r, mat_identity())

P()
P('== MESHES ==')
tri_total = 0; vert_total = 0
gmins = [math.inf]*3; gmaxs = [-math.inf]*3
for mi, mesh in enumerate(js.get('meshes', [])):
    P(f'mesh[{mi}] "{mesh.get("name","")}" primitives={len(mesh.get("primitives",[]))}')
    for pi, prim in enumerate(mesh.get('primitives', [])):
        attrs = prim.get('attributes', {})
        acc0 = attrs.get('POSITION')
        vcount = js['accessors'][acc0]['count'] if acc0 is not None else 0
        icount = 0; tris = 0
        if 'indices' in prim:
            icount = js['accessors'][prim['indices']]['count']; tris = icount//3
        else:
            tris = vcount//3
        tri_total += tris; vert_total += vcount
        mn = js['accessors'][acc0].get('min'); mx = js['accessors'][acc0].get('max')
        P(f'  prim[{pi}] verts={vcount} indices={icount} tris={tris} material={prim.get("material")} mode={prim.get("mode",4)} attrs={list(attrs.keys())} targets={len(prim.get("targets",[]))}')
        if mn and mx:
            P(f'    local_bbox min={[round(v,4) for v in mn]} max={[round(v,4) for v in mx]}')
        # find node using this mesh for world bbox
        for ni, n in enumerate(nodes):
            if n.get('mesh') == mi and mn and mx and ni in world:
                w = world[ni]
                for cx in (mn[0], mx[0]):
                    for cy in (mn[1], mx[1]):
                        for cz in (mn[2], mx[2]):
                            px,py,pz = apply(w, (cx,cy,cz))
                            gmins = [min(gmins[0],px), min(gmins[1],py), min(gmins[2],pz)]
                            gmaxs = [max(gmaxs[0],px), max(gmaxs[1],py), max(gmaxs[2],pz)]
P(f'TOTAL verts={vert_total} tris={tri_total}')
if gmins[0] != math.inf:
    dims = [gmaxs[i]-gmins[i] for i in range(3)]
    P(f'world_bbox min={[round(v,4) for v in gmins]} max={[round(v,4) for v in gmaxs]} dims={[round(v,4) for v in dims]} (glTF units ~ meters, Y-up)')

# morph target summary
if any('targets' in p for m in js.get('meshes',[]) for p in m.get('primitives',[])):
    P('NOTE: morph targets present (blendshapes)')

# --- animations ---
P()
P('== ANIMATIONS ==')
INTERP = {'LINEAR':'LINEAR','STEP':'STEP','CUBICSPLINE':'CUBICSPLINE'}
for ai, a in enumerate(js.get('animations', [])):
    name = a.get('name', f'anim{ai}')
    dur = 0.0
    P(f'animation[{ai}] "{name}" channels={len(a.get("channels",[]))} samplers={len(a.get("samplers",[]))}')
    paths = {}
    for ch in a.get('channels', []):
        t = ch.get('target', {}); s = a['samplers'][ch['sampler']]
        inacc = js['accessors'][s['input']]
        inmax = inacc.get('max', [0])[0]; inmin = inacc.get('min',[0])[0]
        dur = max(dur, inmax)
        node_i = t.get('node'); nname = nodes[node_i].get('name', f'node{node_i}') if node_i is not None else '??'
        path = t.get('path','?')
        key = (nname, path)
        paths.setdefault(key, {'count': inacc['count'], 'interp': s.get('interpolation','LINEAR'), 't0': inmin, 't1': inmax})
        P(f'  target node[{node_i}]"{nname}".{path} keyframes={inacc["count"]} t=[{inmin:.3f}..{inmax:.3f}] interp={s.get("interpolation","LINEAR")}')
    P(f'  DURATION: {dur:.3f}s')

# --- accessors sanity (draco?) ---
if js.get('extensionsRequired') and 'KHR_draco_mesh_compression' in js['extensionsRequired']:
    P()
    P('!! DRACO compressed mesh — Blockbench/converters need draco decoding first')

report = OUT/'report.txt'
report.write_text('\n'.join(out), encoding='utf-8')
print('\n'.join(out))
print(f'\n[written: {report}]')
