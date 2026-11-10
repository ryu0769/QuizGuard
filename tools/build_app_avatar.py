# -*- coding: utf-8 -*-
"""从 sources/app-avatar-night-study.png 生成「刷吧!少年」的头像与启动器图标。

用法（工程根目录）：python tools/build_app_avatar.py

输出：
  res/drawable/app_avatar_night_study.png          App 内圆形头像（512px）
  res/mipmap-*/ic_launcher.png                     Android 7.1 及以下的方形图标
  res/mipmap-*/ic_launcher_round.png               圆形图标
  res/mipmap-xxxhdpi/ic_launcher_avatar.png        Android 8+ 自适应图标前景（108dp 画布）

原图为 1024x1024 圆角方形插画，外有约 40px 白边、圆角半径约 120px；
脚本先裁掉白边并把圆角外变成透明，再按各规格缩放，避免图标出现白色方框。
"""
import os
from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "sources", "app-avatar-night-study.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

PAD = 40           # 白色外边距
RADIUS = 120       # 插画圆角半径
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def make_avatar():
    im = Image.open(SRC).convert("RGBA")
    side = min(im.size) - PAD * 2
    core = im.crop((PAD, PAD, PAD + side, PAD + side))
    mask = Image.new("L", core.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, side - 1, side - 1), radius=RADIUS, fill=255
    )
    core.putalpha(ImageChops.multiply(core.getchannel("A"), mask))
    return core


def resized(avatar, px):
    return avatar.resize((px, px), Image.LANCZOS)


def circular(img):
    px = img.size[0]
    mask = Image.new("L", (px, px), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, px - 1, px - 1), fill=255)
    out = img.copy()
    out.putalpha(ImageChops.multiply(out.getchannel("A"), mask))
    return out


def foreground(avatar, px=432, fill=0.72):
    """108dp 自适应图标前景：插画居中缩到 72%，落在 66dp 安全区内，避免圆形遮罩切掉头/台灯。"""
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    inner = int(px * fill)
    art = resized(avatar, inner)
    offset = (px - inner) // 2
    canvas.alpha_composite(art, (offset, offset))
    return canvas


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print("%-72s %6d KB" % (os.path.relpath(path, ROOT).replace("\\", "/"),
                            os.path.getsize(path) // 1024))


def main():
    avatar = make_avatar()
    print("source:", os.path.relpath(SRC, ROOT).replace("\\", "/"), avatar.size)

    save(resized(avatar, 512),
         os.path.join(RES, "drawable", "app_avatar_night_study.png"))
    for dens, px in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + dens)
        square = resized(avatar, px)
        save(square, os.path.join(folder, "ic_launcher.png"))
        save(circular(square), os.path.join(folder, "ic_launcher_round.png"))
    save(foreground(avatar),
         os.path.join(RES, "mipmap-xxxhdpi", "ic_launcher_avatar.png"))


if __name__ == "__main__":
    main()
