#!/usr/bin/env python3
from flask import Flask, request, jsonify, Response, stream_with_context, send_from_directory
from flask_cors import CORS
import yt_dlp, requests, re, os, subprocess
from bs4 import BeautifulSoup

BASE_DIR = os.path.expanduser("~/music_api")
app = Flask(__name__)
CORS(app)

YDL_SEARCH = {"quiet":True,"no_warnings":True,"extract_flat":True,"default_search":"ytsearch","noplaylist":True}
YDL_INFO   = {"quiet":True,"no_warnings":True,"format":"bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio","noplaylist":True}

def yt_search(q, limit=10):
    o = dict(YDL_SEARCH); o["playlistend"] = limit
    with yt_dlp.YoutubeDL(o) as y:
        info = y.extract_info(f"ytsearch{limit}:{q}", download=False)
        return info.get("entries", []) if info else []

def yt_info(vid):
    with yt_dlp.YoutubeDL(YDL_INFO) as y:
        return y.extract_info(f"https://www.youtube.com/watch?v={vid}", download=False)

def pick_audio(d):
    for f in d.get("formats", []):
        if f.get("acodec") != "none" and f.get("vcodec") == "none":
            url = f.get("url", "")
            ext = f.get("ext", "")
            if "m3u8" in url or ext == "m3u8" or "manifest" in url:
                continue
            return f
    return None

@app.route("/")
def root():
    return send_from_directory(BASE_DIR, "player.html")

@app.route("/player")
def player():
    return send_from_directory(BASE_DIR, "player.html")

@app.route("/favicon.ico")
def favicon():
    return Response(status=204)

@app.route("/health")
def health():
    return jsonify({"status":"healthy"})

@app.route("/search")
def search():
    q = request.args.get("q","").strip()
    limit = max(1, min(int(request.args.get("limit",10)), 30))
    if not q:
        return jsonify({"success":False,"message":"q wajib"}), 400
    try:
        entries = yt_search(q, limit)
    except Exception as e:
        return jsonify({"success":False,"message":f"Search error: {e}"}), 500
    results = []
    for e in entries:
        if not e: continue
        vid = e.get("id") or ""
        results.append({
            "id": vid,
            "title": e.get("title") or "Unknown",
            "artist": e.get("uploader") or e.get("channel"),
            "duration": e.get("duration"),
            "thumbnail": e.get("thumbnail") or (f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg" if vid else None),
            "url": f"/stream/{vid}",
        })
    return jsonify({"success":True,"query":q,"results":results})

@app.route("/api/lyrics")
def get_lyrics():
    from urllib.parse import quote
    
    raw_title = request.args.get("title", "").strip()
    raw_artist = request.args.get("artist", "").strip()
    
    if not raw_title:
        return jsonify({"success": False, "message": "Judul kosong"}), 400

    # Bersihin Judul
    clean_title = re.sub(r'[\(\[].*?[\)\]]', '', raw_title)
    clean_title = re.sub(r'(?i)(official|video|lyrics|audio|music|hd|hq|1 hour)', '', clean_title)
    clean_title = re.sub(r'\|.*', '', clean_title).strip()
    clean_artist = re.sub(r'(?i)(- topic|vevo)', '', raw_artist).strip()
    
    # 1. COBA LRCLIB
    queries = []
    if clean_title and clean_artist:
        queries.append(f"{clean_title} {clean_artist}")
    if clean_title:
        queries.append(clean_title)
        
    for q in queries:
        url = f"https://lrclib.net/api/search?q={quote(q)}"
        try:
            r = requests.get(url, timeout=10)
            if r.status_code == 200:
                data = r.json()
                if data:
                    best = next((d for d in data if d.get("syncedLyrics")), None)
                    if not best:
                        best = next((d for d in data if d.get("plainLyrics")), data[0])
                    return jsonify({
                        "success": True,
                        "synced": best.get("syncedLyrics"),
                        "plain": best.get("plainLyrics")
                    })
        except Exception:
            pass
            
    # 2. FALLBACK KE GENIUS
    try:
        search_url = f"https://genius.com/api/search/multi?q={quote(clean_title + ' ' + clean_artist)}"
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
        r = requests.get(search_url, headers=headers, timeout=10).json()
        
        song_url = None
        for section in r.get("response", {}).get("sections", []):
            for hit in section.get("hits", []):
                if hit.get("type") == "song":
                    song_url = hit["result"]["url"]
                    break
            if song_url: break
            
        if song_url:
            page = requests.get(song_url, headers=headers, timeout=10)
            # Pakai html.parser (bawaan Python, gak butuh lxml)
            soup = BeautifulSoup(page.text, "html.parser") 
            lyrics_div = soup.find("div", {"data-lyrics-container": "true"})
            if lyrics_div:
                for br in lyrics_div.find_all("br"):
                    br.replace_with("\n")
                plain_lyrics = lyrics_div.get_text()
                plain_lyrics = re.sub(r'\[.*?\]', '', plain_lyrics) 
                plain_lyrics = re.sub(r'\n{3,}', '\n\n', plain_lyrics).strip()
                
                return jsonify({
                    "success": True,
                    "synced": None,
                    "plain": plain_lyrics
                })
    except Exception as e:
        print("Genius error:", e)
        
    return jsonify({"success": False, "message": "Lirik tidak ditemukan di LRCLIB maupun Genius."})

@app.route("/info/<vid>")
def info(vid):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{11}", vid):
        return jsonify({"success":False,"message":"Invalid video id"}), 400
    try:
        d = yt_info(vid)
    except Exception as e:
        return jsonify({"success":False,"message":f"Info error: {e}"}), 500
    f = pick_audio(d)
    return jsonify({
        "id": vid,
        "title": d.get("title"),
        "duration": d.get("duration"),
        "thumbnail": d.get("thumbnail"),
        "uploader": d.get("uploader"),
        "audio_format": {"format_id":f.get("format_id"),"ext":f.get("ext"),"abr":f.get("abr"),"filesize":f.get("filesize")} if f else None,
        "stream_url": f"/stream/{vid}",
    })

@app.route("/stream/<vid>")
def stream(vid):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{11}", vid):
        return jsonify({"success":False,"message":"Invalid video id"}), 400
    try:
        d = yt_info(vid)
    except Exception as e:
        return jsonify({"success":False,"message":f"Stream error: {e}"}), 500
    f = pick_audio(d)
    if not f or not f.get("url"):
        return jsonify({"success":False,"message":"No audio stream"}), 404
    audio_url = f["url"]
    headers = {"User-Agent":"Mozilla/5.0","Accept":"*/*"}
    rng = request.headers.get("Range")
    if rng: headers["Range"] = rng
    up = requests.get(audio_url, headers=headers, stream=True, allow_redirects=True)
    def gen():
        try:
            for c in up.iter_content(65536):
                if c: yield c
        finally:
            up.close()
    rh = {}
    for k in ["Content-Length","Content-Type","Accept-Ranges","Content-Range"]:
        if k in up.headers: rh[k] = up.headers[k]
    return Response(stream_with_context(gen()),
        status=up.status_code if up.status_code in (200,206) else 200,
        headers=rh, mimetype=up.headers.get("Content-Type","audio/mpeg"))

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8000, threaded=True)
