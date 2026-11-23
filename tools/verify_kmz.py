#!/usr/bin/env python3
"""Verify a Waykeep/OpenTracks KMZ: distance, lap count, per-lap length.

Uses Vincenty on WGS84, the same geodesic Android's Location.distanceTo()
uses, so the distance should match what the app computed.

Laps are counted by axis crossings (each lap crosses the mean latitude and
the mean longitude exactly twice), which does not depend on where recording
started.

usage: python3 verify_kmz.py <file.kmz>
"""
import datetime
import math
import re
import sys
import zipfile

A = 6378137.0
F = 1 / 298.257223563
B = (1 - F) * A


def vincenty(lat1, lon1, lat2, lon2):
    L = math.radians(lon2 - lon1)
    U1 = math.atan((1 - F) * math.tan(math.radians(lat1)))
    U2 = math.atan((1 - F) * math.tan(math.radians(lat2)))
    sU1, cU1, sU2, cU2 = math.sin(U1), math.cos(U1), math.sin(U2), math.cos(U2)
    lam = L
    for _ in range(200):
        sl, cl = math.sin(lam), math.cos(lam)
        ss = math.hypot(cU2 * sl, cU1 * sU2 - sU1 * cU2 * cl)
        if ss == 0:
            return 0.0
        cs = sU1 * sU2 + cU1 * cU2 * cl
        sig = math.atan2(ss, cs)
        sa = cU1 * cU2 * sl / ss
        c2a = 1 - sa * sa
        c2sm = cs - 2 * sU1 * sU2 / c2a if c2a else 0
        C = F / 16 * c2a * (4 + F * (4 - 3 * c2a))
        prev = lam
        lam = L + (1 - C) * F * sa * (sig + C * ss * (c2sm + C * cs * (-1 + 2 * c2sm * c2sm)))
        if abs(lam - prev) < 1e-12:
            break
    u2 = c2a * (A * A - B * B) / (B * B)
    Aa = 1 + u2 / 16384 * (4096 + u2 * (-768 + u2 * (320 - 175 * u2)))
    Bb = u2 / 1024 * (256 + u2 * (-128 + u2 * (74 - 47 * u2)))
    ds = Bb * ss * (c2sm + Bb / 4 * (cs * (-1 + 2 * c2sm * c2sm)
                                   - Bb / 6 * c2sm * (-3 + 4 * ss * ss) * (-3 + 4 * c2sm * c2sm)))
    return B * Aa * (sig - ds)


def load(path):
    """Returns (time, lat, lon, accuracy_m or None) for every stored fix."""
    with zipfile.ZipFile(path) as z:
        kml = z.read("doc.kml").decode("utf-8")
    body = kml[kml.find("<MultiTrack>"):kml.find("</MultiTrack>")]
    points = []
    for track in re.findall(r"<Track>(.*?)</Track>", body, re.S):
        whens = re.findall(r"<when>(.*?)</when>", track)
        coords = re.findall(r"<coord>([^<]*)</coord>", track)
        accs = [v.strip() for v in re.findall(
            r'<value>([^<]*)</value>',
            re.search(r'SimpleArrayData name="accuracy_horizontal">(.*?)</SimpleArrayData>', track, re.S).group(1)
            if re.search(r'SimpleArrayData name="accuracy_horizontal"', track) else "", re.S)]
        for i, (when, coord) in enumerate(zip(whens, coords)):
            if not coord.strip():
                continue  # segment start without a fix
            lon, lat, _ele = (float(v) for v in coord.split())
            acc = float(accs[i]) if i < len(accs) and accs[i] else None
            points.append((datetime.datetime.fromisoformat(when), lat, lon, acc))
    return points


def d(a, b):
    return vincenty(a[1], a[2], b[1], b[2])


def distance(pts):
    return sum(d(a, b) for a, b in zip(pts, pts[1:]))


def count_laps(pts):
    """Each lap crosses the mean latitude and longitude twice."""
    mlat = sum(p[1] for p in pts) / len(pts)
    mlon = sum(p[2] for p in pts) / len(pts)

    def crossings(v, mean):
        s = [x - mean for x in v]
        return sum(1 for a, b in zip(s, s[1:]) if (a < 0 <= b) or (a > 0 >= b))

    return max(crossings([p[1] for p in pts], mlat),
               crossings([p[2] for p in pts], mlon)) / 2


def main(path):
    pts = load(path)
    total = distance(pts)
    secs = (pts[-1][0] - pts[0][0]).total_seconds()

    # A coarse first fix (network provisional start) is stored, but the hop to the
    # first GPS fix is not real movement.
    first = next((i for i, p in enumerate(pts) if p[3] is not None), 0)
    clean = pts[first + 1:] if pts[first][3] and pts[first][3] > 50 else pts[first:]
    laps = count_laps(clean)

    print(f"points           {len(pts)}")
    print(f"duration         {secs:.0f} s")
    print(f"distance stored  {total:.1f} m  ({total / 1000:.2f} km)")
    if clean is not pts[first:]:
        print(f"distance clean   {distance(clean):.1f} m  "
              f"(dropped {pts[first][3]:.0f} m-accuracy start, hop {total - distance(clean):.1f} m)")
    print(f"laps             {laps:.1f}")
    print(f"mean lap         {total / laps:.1f} m stored | {distance(clean) / laps:.1f} m clean")
    print(f"mean speed       {total / secs:.2f} m/s ({total / secs * 3.6:.2f} km/h)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
