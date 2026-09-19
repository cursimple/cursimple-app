#!/usr/bin/env python3
"""生成小组件选择器用的位图预览。

为什么要位图：不少国产启动器（EMUI / HarmonyOS、vivo 的挂件选择器尤其明显）在读
`android:previewImage` 时按 BitmapDrawable 处理，遇到矢量图会直接跳过这一项，
于是应用明明装了，选择器里却找不到这个小组件。

为什么画成真样子而不是示意条：选择器里那张图就是用户决定装不装的全部依据。
之前用灰条占位，看着像半成品草图，和装上之后的样子对不上。这里按小组件的
默认绿色主题、真实字号与真实文案画一遍，所见即所得。

运行：python3 scripts/generate_widget_previews.py
输出：feature-widget/src/main/res/drawable-nodpi/widget_preview_*.png
"""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# 取自 res/drawable/widget_bg_*_green 与 res/values/colors.xml：
# 默认主题色是绿色，预览必须画成新用户装上之后真正看到的那一套
CARD = (221, 239, 228, 255)        # widget_bg_card_green
SURFACE = (184, 220, 201, 255)     # widget_bg_surface_green
BADGE = (211, 227, 251, 255)       # widget_bg_badge
INK = (32, 36, 42, 255)            # widget_row_title
MUTED = (102, 112, 127, 255)       # widget_row_subtitle
TIME = (49, 95, 137, 255)          # widget_row_time
BADGE_INK = (36, 78, 114, 255)     # widget_badge_text

SCALE = 3  # dp → px，画大一些让选择器缩放后仍然清楚

# 系统自带的中文字体；换机器跑不出来时改这里
FONT_CANDIDATES = [
    ("/System/Library/Fonts/Hiragino Sans GB.ttc", 0),
    ("/System/Library/Fonts/STHeiti Medium.ttc", 0),
    ("/Library/Fonts/Arial Unicode.ttf", 0),
]


# 预览里的示例文案，和 res/values*/strings.xml 的 widget_preview_* 一一对应。
# 选择器按系统语言挑 drawable-<语言>-nodpi，所以每种语言各出一套图。
LOCALES = {
    "": {  # 默认：简体中文
        "date": "9月20日 · 今天", "weekday": "周六",
        "nodes1": "1-2节", "nodes2": "3-4节",
        "time1": "08:00-09:35", "time2": "10:05-11:40",
        "course1": "算法与数据结构", "course2": "毛泽东思想概论",
        "place1": "东 13-C-315", "place2": "东 11-A-207",
        "next_title": "下一节课", "next_badge": "20分钟后",
        "next_line": "08:00-09:35 · 东 13-C-315",
        "rem_title": "课程提醒", "rem_badge": "已开启",
        "rem1": "今天 07:50  算法与数据结构", "rem_sub1": "课前 10 分钟 · 东 13-C-315",
        "rem2": "明天 09:55  毛泽东思想概论", "rem_sub2": "课前 10 分钟 · 东 11-A-207",
    },
    "en": {
        "date": "Sep 20 · Today", "weekday": "Saturday",
        "nodes1": "P1-2", "nodes2": "P3-4",
        "time1": "08:00-09:35", "time2": "10:05-11:40",
        "course1": "Data Structures", "course2": "Modern History",
        "place1": "East 13-C-315", "place2": "East 11-A-207",
        "next_title": "Next class", "next_badge": "In 20 min",
        "next_line": "08:00-09:35 · East 13-C-315",
        "rem_title": "Class reminders", "rem_badge": "On",
        "rem1": "Today 07:50  Data Structures", "rem_sub1": "10 min before · East 13-C-315",
        "rem2": "Tomorrow 09:55  Modern History", "rem_sub2": "10 min before · East 11-A-207",
    },
    "zh-rTW": {
        "date": "9月20日 · 今天", "weekday": "週六",
        "nodes1": "1-2節", "nodes2": "3-4節",
        "time1": "08:00-09:35", "time2": "10:05-11:40",
        "course1": "演算法與資料結構", "course2": "近代史綱要",
        "place1": "東 13-C-315", "place2": "東 11-A-207",
        "next_title": "下一節課", "next_badge": "20分鐘後",
        "next_line": "08:00-09:35 · 東 13-C-315",
        "rem_title": "課程提醒", "rem_badge": "已開啟",
        "rem1": "今天 07:50  演算法與資料結構", "rem_sub1": "課前 10 分鐘 · 東 13-C-315",
        "rem2": "明天 09:55  近代史綱要", "rem_sub2": "課前 10 分鐘 · 東 11-A-207",
    },
}


def _px(value: float) -> int:
    return int(round(value * SCALE))


def _font(size_sp: float) -> ImageFont.FreeTypeFont:
    for path, index in FONT_CANDIDATES:
        try:
            return ImageFont.truetype(path, _px(size_sp), index=index)
        except OSError:
            continue
    raise SystemExit("找不到可用的中文字体，请在 FONT_CANDIDATES 里补一个")


def _card(width_dp: int, height_dp: int) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (_px(width_dp), _px(height_dp)), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        (0, 0, _px(width_dp) - 1, _px(height_dp) - 1),
        radius=_px(22),
        fill=CARD,
    )
    return image, draw


def _text(draw: ImageDraw.ImageDraw, x: float, y: float, value: str, size: float, fill) -> None:
    draw.text((_px(x), _px(y)), value, font=_font(size), fill=fill)


def _surface(draw: ImageDraw.ImageDraw, x: float, y: float, w: float, h: float, radius: float = 14) -> None:
    draw.rounded_rectangle((_px(x), _px(y), _px(x + w), _px(y + h)), radius=_px(radius), fill=SURFACE)


def _chevron(draw: ImageDraw.ImageDraw, cx: float, cy: float, pointing_left: bool) -> None:
    """翻页箭头。字体里不一定有 ‹ ›，直接画两笔更稳。"""
    dx = -4 if pointing_left else 4
    draw.line(
        [(_px(cx - dx), _px(cy - 5)), (_px(cx + dx), _px(cy)), (_px(cx - dx), _px(cy + 5))],
        fill=MUTED,
        width=_px(1.6),
        joint="curve",
    )


def _center(draw: ImageDraw.ImageDraw, width_dp: float, y: float, value: str, size: float, fill) -> None:
    """水平居中。英文比中文宽得多，坐标必须按实际文字宽度算，不能写死。"""
    font = _font(size)
    w = draw.textlength(value, font=font)
    draw.text(((_px(width_dp) - w) / 2, _px(y)), value, font=font, fill=fill)


def _badge(draw: ImageDraw.ImageDraw, width_dp: float, y: float, value: str) -> None:
    """右上角的徽标，底框跟着文字宽度走。"""
    font = _font(10)
    w = draw.textlength(value, font=font)
    right = _px(width_dp - 12)
    left = right - w - _px(12)
    draw.rounded_rectangle((left, _px(y), right, _px(y + 16)), radius=_px(10), fill=BADGE)
    draw.text((left + _px(6), _px(y + 1)), value, font=font, fill=BADGE_INK)


def _course_row(
    draw: ImageDraw.ImageDraw,
    x: float,
    y: float,
    w: float,
    nodes: str,
    time_range: str,
    title: str,
    place: str,
) -> None:
    """一行课，字号与 widget_schedule_course_row 布局一致。"""
    _surface(draw, x, y, w, 50)
    _text(draw, x + 12, y + 6, nodes, 9, MUTED)
    nodes_width = draw.textlength(nodes, font=_font(9)) / SCALE
    _text(draw, x + 12 + nodes_width + 6, y + 6, time_range, 9, TIME)
    _text(draw, x + 12, y + 19, title, 12, INK)
    _text(draw, x + 12, y + 35, place, 10, MUTED)


def today_preview(t: dict) -> Image.Image:
    """每日课程：头部一天的日期加左右翻页，下面两行课。"""
    width, height = 220, 148
    image, draw = _card(width, height)

    # 左右翻页键：和真机一样是两个圆底方块
    _surface(draw, 8, 8, 30, 30, radius=20)
    _surface(draw, width - 38, 8, 30, 30, radius=20)
    _chevron(draw, 23, 23, pointing_left=True)
    _chevron(draw, width - 23, 23, pointing_left=False)

    _center(draw, width, 11, t["date"], 14, INK)
    _center(draw, width, 28, t["weekday"], 10, MUTED)

    _course_row(draw, 8, 44, width - 16, t["nodes1"], t["time1"], t["course1"], t["place1"])
    _course_row(draw, 8, 98, width - 16, t["nodes2"], t["time2"], t["course2"], t["place2"])
    return image


def next_preview(t: dict) -> Image.Image:
    """下一节课：标题加徽标，下面一条当前要上的课。"""
    width, height = 220, 64
    image, draw = _card(width, height)

    _text(draw, 12, 8, t["next_title"], 14, INK)
    _badge(draw, width, 9, t["next_badge"])

    _surface(draw, 8, 30, width - 16, 26)
    _text(draw, 20, 33, t["course1"], 12, INK)
    _text(draw, 20, 46, t["next_line"], 9, MUTED)
    return image


def reminder_preview(t: dict) -> Image.Image:
    """课程提醒：两条待响的提醒。"""
    width, height = 220, 118
    image, draw = _card(width, height)

    _text(draw, 12, 8, t["rem_title"], 14, INK)
    _badge(draw, width, 9, t["rem_badge"])

    _surface(draw, 8, 32, width - 16, 36)
    _text(draw, 20, 36, t["rem1"], 11, INK)
    _text(draw, 20, 51, t["rem_sub1"], 9, MUTED)

    _surface(draw, 8, 72, width - 16, 36)
    _text(draw, 20, 76, t["rem2"], 11, INK)
    _text(draw, 20, 91, t["rem_sub2"], 9, MUTED)
    return image


def main() -> None:
    res_dir = Path(__file__).resolve().parent.parent / "feature-widget/src/main/res"
    for locale, texts in LOCALES.items():
        suffix = f"-{locale}" if locale else ""
        out_dir = res_dir / f"drawable{suffix}-nodpi"
        out_dir.mkdir(parents=True, exist_ok=True)
        previews = {
            "widget_preview_today.png": today_preview(texts),
            "widget_preview_next.png": next_preview(texts),
            "widget_preview_reminder.png": reminder_preview(texts),
        }
        for name, image in previews.items():
            path = out_dir / name
            image.save(path, format="PNG", optimize=True)
            print(f"wrote {out_dir.name}/{name} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
