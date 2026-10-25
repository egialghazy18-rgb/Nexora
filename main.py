#!/usr/bin/env python3
"""
Music API Server — search, stream, metadata
Jalan: uvicorn main:app --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI, HTTPException, Query, Response
from fastapi.responses import StreamingResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
import yt_dlp
import httpx
import os
import re

app = FastAPI(title="Scarlett Music API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------- yt-dlp config ----------
YDL_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "extract_flat": False,
    "format": "bestaudio/best",
    "noplaylist": True,
    "default_search": "ytsearch",
    "source_address": "0.0.0.0",
}

YDL_SEARCH_OPTS = {
    "quiet": True,
    "no_warnings": True,
    "extract_flat": True,
    "default_search": "ytsearch",
    "noplaylist": True,
}

# ---------- models ----------
class Track(BaseModel):
    id: str
    title: str
    artist: Optional[str] = None
    duration: Optional[int] = None
    thumbnail: Optional[str] = None
    url: Optional[str] = None

class SearchResponse(BaseModel):
    success: bool
    query: str
    results: list[Track]

# ---------- helpers ----------
def yt_search(query: str, limit: int = 10) -> list[dict]:
    opts = dict(YDL_SEARCH_OPTS)
    opts["playlistend"] = limit
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(f"ytsearch{limit}:{query}", download=False)
        entries = info.get("entries", []) if info else []
        return entries

def yt_info(video_id: str) -> dict:
    with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
        return ydl.extract_info(f"https://www.youtube.com/watch?v={video_id}", download=False)

# ---------- endpoints ----------
@app.get("/")
def root():
    return {"name": "Scarlett Music API", "version": "1.0.0", "status": "ok"}

@app.get("/health")
def health():
    return {"status": "healthy"}

@app.get("/search", response_model=SearchResponse)
def search(q: str = Query(..., min_length=1), limit: int = Query(10, ge=1, le=30)):
    try:
        entries = yt_search(q, limit)
    except Exception as e:
        raise HTTPException(500, f"Search error: {e}")

    tracks = []
    for e in entries:
        if not e: continue
        vid = e.get("id") or ""
        tracks.append(Track(
            id=vid,
            title=e.get("title") or "Unknown",
            artist=e.get("uploader") or e.get("channel") or None,
            duration=e.get("duration"),
            thumbnail=e.get("thumbnail") or (f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg" if vid else None),
            url=f"/stream/{vid}",
        ))
    return SearchResponse(success=True, query=q, results=tracks)

@app.get("/info/{video_id}")
def info(video_id: str):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{11}", video_id):
        raise HTTPException(400, "Invalid video id")
    try:
        data = yt_info(video_id)
    except Exception as e:
        raise HTTPException(500, f"Info error: {e}")

    formats = []
    for f in data.get("formats", []):
        if f.get("acodec") != "none" and f.get("vcodec") == "none":
            formats.append({
                "format_id": f.get("format_id"),
                "ext": f.get("ext"),
                "abr": f.get("abr"),
                "filesize": f.get("filesize"),
                "url": f.get("url"),
            })

    return {
        "id": video_id,
        "title": data.get("title"),
        "duration": data.get("duration"),
        "thumbnail": data.get("thumbnail"),
        "uploader": data.get("uploader"),
        "audio_formats": formats[:10],
        "stream_url": f"/stream/{video_id}",
    }

@app.get("/stream/{video_id}")
async def stream(video_id: str, range: str = None):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{11}", video_id):
        raise HTTPException(400, "Invalid video id")

    try:
        data = yt_info(video_id)
    except Exception as e:
        raise HTTPException(500, f"Stream info error: {e}")

    # pilih audio-only format terbaik
    audio_url = None
    for f in data.get("formats", []):
        if f.get("acodec") != "none" and f.get("vcodec") == "none":
            audio_url = f.get("url")
            break
    if not audio_url:
        # fallback
        audio_url = data.get("url")
    if not audio_url:
        raise HTTPException(404, "No audio stream")

    headers = {
        "User-Agent": "Mozilla/5.0",
        "Accept": "*/*",
    }
    if range:
        headers["Range"] = range

    async def proxy():
        async with httpx.AsyncClient(timeout=None, follow_redirects=True) as client:
            async with client.stream("GET", audio_url, headers=headers) as r:
                async for chunk in r.aiter_bytes(chunk_size=65536):
                    yield chunk

    # ambil header dari upstream (butuh satu request HEAD/GET dulu)
    async with httpx.AsyncClient(timeout=10, follow_redirects=True) as client:
        head = await client.head(audio_url, headers=headers)

    resp_headers = {}
    for k in ["content-length","content-type","accept-ranges","content-range"]:
        if k in head.headers:
            resp_headers[k] = head.headers[k]

    return StreamingResponse(
        proxy(),
        status_code=head.status_code if head.status_code in (200,206) else 200,
        headers=resp_headers,
        media_type=head.headers.get("content-type", "audio/mpeg"),
    )

@app.get("/download/{video_id}")
def download(video_id: str):
    if not re.fullmatch(r"[A-Za-z0-9_\-]{11}", video_id):
        raise HTTPException(400, "Invalid video id")
    return {"message": "Gunakan /stream untuk streaming langsung", "stream_url": f"/stream/{video_id}"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
