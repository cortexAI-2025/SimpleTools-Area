import sys
from geographiclib.geodesic import Geodesic
from geographiclib.polygonarea import PolygonArea
g=Geodesic.WGS84
def area(pts):
    p=PolygonArea(g)
    for la,lo in pts: p.AddPoint(la,lo)
    n,per,a=p.Compute(False,True); return abs(a),per
print('cell1', area([(0,0),(0,1),(1,1),(1,0)]))
def sq(lat,lon,s):
    pts=[(lat,lon)]; la,lo=lat,lon
    for a in [90,180,270]:
        r=g.Direct(la,lo,a,s); la,lo=r['lat2'],r['lon2']; pts.append((la,lo))
    return pts
for name,(lat,lon,s) in {'paris100':(48.8566,2.3522,100.0),'eq1000':(0.0,10.0,1000.0),'lat70_500':(70.0,20.0,500.0)}.items():
    pts=sq(lat,lon,s); print(name,[(round(a,9),round(b,9)) for a,b in pts], area(pts))
pts=[(45.75,4.85),(45.75,4.8511),(45.7506,4.8511),(45.7506,4.8505),(45.7511,4.8505),(45.7511,4.85)]
print('L',area(pts))
print('anti',area([(10,179.9),(10,-179.9),(10.2,-179.9),(10.2,179.9)]))
print('tri',area([(0,0),(0,0.01),(0.01,0)]))
r=g.Inverse(-(37+57/60+3.72030/3600),144+25/60+29.52440/3600,-(37+39/60+10.15610/3600),143+55/60+35.38390/3600); print('vinc',r['s12'])
