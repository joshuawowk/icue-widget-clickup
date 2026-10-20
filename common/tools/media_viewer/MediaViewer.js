/*
 * MediaViewer - displays and transforms user-selected images and videos.
 * CORSAIR iCUE common tool, bundled so the widget is self-contained.
 * Source: https://github.com/Corsair-Labs/icue-widget-builder
 *         (skills/icue-widget-builder/references/common-tools.md), MIT License, Copyright (c) 2026 CORSAIR.
 */
class MediaViewer {
  constructor(options = {}) {
    this.container = options.container;
    this.onMediaLoaded = options.onMediaLoaded || (() => {});
    this.onMediaError = options.onMediaError || (() => {});
    this.debug = options.debug || false;
    this.positionContainer = null; this.scaleContainer = null; this.rotationContainer = null;
    this.mediaElement = null; this.currentMediaPath = null;
    this.supportedImageFormats = ["jpg","jpeg","png","gif","bmp","webp","svg","ico"];
    this.supportedVideoFormats = ["mp4","webm","ogg","mov","avi","mkv"];
    if (!this.container) throw new Error("MediaViewer: container is required");
  }
  getFileExtension(p) { return p.split(".").pop().toLowerCase(); }
  isImageFile(e) { return this.supportedImageFormats.includes(e); }
  isVideoFile(e) { return this.supportedVideoFormats.includes(e); }
  log(...a) { if (this.debug) console.log("[MediaViewer]", ...a); }
  warn(...a) { if (this.debug) console.warn("[MediaViewer]", ...a); }
  error(...a) { console.error("[MediaViewer]", ...a); }
  createImageElement(filePath) {
    const img = document.createElement("img");
    img.src = filePath;
    img.onerror = () => { this.error("Failed to load image:", filePath); this.onMediaError(new Error("Failed to load image: " + filePath)); };
    img.onload  = () => { this.log("Image loaded:", filePath); this.onMediaLoaded(img); };
    return img;
  }
  createVideoElement(filePath) {
    const video = document.createElement("video");
    video.controls = false; video.autoplay = true; video.loop = true; video.muted = true; video.playsInline = true;
    video.addEventListener("loadeddata", () => {
      video.play().then(() => { this.onMediaLoaded(video); }).catch(e => { this.error("Error playing video:", e); this.onMediaError(e); });
    });
    video.addEventListener("error", e => { this.error("Video error:", e); this.onMediaError(new Error("Video error: " + (video.error?.message || "Unknown"))); });
    video.src = filePath;
    return video;
  }
  createMediaElement(filePath) {
    const ext = this.getFileExtension(filePath);
    if (this.isImageFile(ext)) return this.createImageElement(filePath);
    if (this.isVideoFile(ext)) return this.createVideoElement(filePath);
    this.error("Unsupported format:", ext); this.onMediaError(new Error("Unsupported format: " + ext)); return null;
  }
  createContainers() {
    this.positionContainer = document.createElement("div"); this.positionContainer.className = "position-container";
    this.scaleContainer    = document.createElement("div"); this.scaleContainer.className    = "scale-container";
    this.rotationContainer = document.createElement("div"); this.rotationContainer.className = "rotation-container";
    this.rotationContainer.appendChild(this.mediaElement);
    this.scaleContainer.appendChild(this.rotationContainer);
    this.positionContainer.appendChild(this.scaleContainer);
    return this.positionContainer;
  }
  applyTransform(params = {}) {
    if (!this.positionContainer) return;
    const baseWidth = params.baseWidth || 100; const baseHeight = params.baseHeight || 100;
    const scaleValue = params.scale !== undefined ? params.scale : 1;
    const posX = params.positionX || 0; const posY = params.positionY || 0; const rotation = params.angle || 0;
    const minScale = Math.min(this.container.clientWidth / baseWidth, this.container.clientHeight / baseHeight);
    this.positionContainer.style.transform = `translate(${posX * minScale}px, ${posY * minScale}px)`;
    this.scaleContainer.style.transform    = `scale(${scaleValue * minScale})`;
    this.rotationContainer.style.transform = `rotate(${rotation}deg)`;
  }
  loadMedia(params = {}) {
    const filePath = params.path; if (!filePath) { this.clear(); return; }
    const decoded = decodeURIComponent(filePath);
    if (this.currentMediaPath === decoded) { this.applyTransform(params); return; }
    this.clear();
    this.mediaElement = this.createMediaElement(decoded); if (!this.mediaElement) return;
    this.container.appendChild(this.createContainers());
    this.container.style.visibility = "visible";
    this.applyTransform(params); this.currentMediaPath = decoded;
  }
  clear() {
    this.container.innerHTML = ""; this.container.style.visibility = "hidden";
    this.positionContainer = this.scaleContainer = this.rotationContainer = this.mediaElement = this.currentMediaPath = null;
  }
  destroy() { this.clear(); this.container = this.onMediaLoaded = this.onMediaError = null; }
}
