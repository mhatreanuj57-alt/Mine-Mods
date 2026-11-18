import json,struct,numpy as np
def load(f):
    d=open(f,'rb').read()
    l=struct.unpack('<I',d[12:16])[0]
    j=json.loads(d[20:20+l])
    off=20+l
    bl=struct.unpack('<I',d[off:off+4])[0]
    bin_=d[off+8:off+8+bl]
    return j,bin_
def acc(j,b,i):
    a=j['accessors'][i]; bv=j['bufferViews'][a['bufferView']]
    n={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']]
    dt={5126:'<f4',5123:'<u2',5125:'<u4',5121:'u1'}[a['componentType']]
    o=bv.get('byteOffset',0)+a.get('byteOffset',0)
    arr=np.frombuffer(b,dtype=dt,count=a['count']*n,offset=o).reshape(a['count'],n)
    return arr
