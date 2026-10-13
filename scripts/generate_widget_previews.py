#!/usr/bin/env python3
"""Generate localized bitmap widget previews matching resource colors and text. Bitmap previews preserve compatibility with launchers that reject vector preview images."""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

# Match the resource palette used by the default installed widget.
CARD = (221, 239, 228, 255)        # widget_bg_card_green
SURFACE = (184, 220, 201, 255)     # widget_bg_surface_green
BADGE = (211, 227, 251, 255)       # widget_bg_badge
INK = (32, 36, 42, 255)            # widget_row_title
MUTED = (102, 112, 127, 255)       # widget_row_subtitle
TIME = (49, 95, 137, 255)          # widget_row_time
BADGE_INK = (36, 78, 114, 255)     # widget_badge_text

SCALE = 3

FONT_CANDIDATES = [
    ("/System/Library/Fonts/Hiragino Sans GB.ttc", 0),
    ("/System/Library/Fonts/STHeiti Medium.ttc", 0),
    ("/Library/Fonts/Arial Unicode.ttf", 0),
]


# Generate one bitmap set per locale, matching widget_preview resource strings.
LOCALES = {
    "": {
        "date": "9月20日 · 今天", "weekday": "周六",
        "nodes1": "1-2节", "nodes2": "事务",
        "time1": "08:00-09:35", "time2": "12:30-13:00",
        "course1": "算法与数据结构", "course2": "社团例会",
        "place1": "东 13-C-315", "place2": "学生活动中心",
        "next_title": "下一节课", "next_badge": "20分钟后",
        "next_line": "08:00-09:35 · 东 13-C-315",
        "rem_title": "课程提醒", "rem_badge": "已开启",
        "rem1": "今天 07:50  算法与数据结构", "rem_sub1": "课前 10 分钟 · 东 13-C-315",
        "rem2": "明天 09:55  毛泽东思想概论", "rem_sub2": "课前 10 分钟 · 东 11-A-207",
    },
    "en": {
        "date": "Sep 20 · Today", "weekday": "Saturday",
        "nodes1": "P1-2", "nodes2": "Event",
        "time1": "08:00-09:35", "time2": "12:30-13:00",
        "course1": "Data Structures", "course2": "Club meeting",
        "place1": "East 13-C-315", "place2": "Student Center",
        "next_title": "Next class", "next_badge": "In 20 min",
        "next_line": "08:00-09:35 · East 13-C-315",
        "rem_title": "Class reminders", "rem_badge": "On",
        "rem1": "Today 07:50  Data Structures", "rem_sub1": "10 min before · East 13-C-315",
        "rem2": "Tomorrow 09:55  Modern History", "rem_sub2": "10 min before · East 11-A-207",
    },
    "zh-rTW": {
        "date": "9月20日 · 今天", "weekday": "週六",
        "nodes1": "1-2節", "nodes2": "事務",
        "time1": "08:00-09:35", "time2": "12:30-13:00",
        "course1": "演算法與資料結構", "course2": "社團例會",
        "place1": "東 13-C-315", "place2": "學生活動中心",
        "next_title": "下一節課", "next_badge": "20分鐘後",
        "next_line": "08:00-09:35 · 東 13-C-315",
        "rem_title": "課程提醒", "rem_badge": "已開啟",
        "rem1": "今天 07:50  演算法與資料結構", "rem_sub1": "課前 10 分鐘 · 東 13-C-315",
        "rem2": "明天 09:55  近代史綱要", "rem_sub2": "課前 10 分鐘 · 東 11-A-207",
    },
}


def _px(value: float) -> int:
    return int(round(value * SCALE))


def _square_bounds(x: float, y: float, side: float) -> tuple[int, int, int, int]:
    return (_px(x), _px(y), _px(x + side), _px(y + side))


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
    """今日安排：头部一天的日期加左右翻页，下面一行课、一行事务。"""
    width, height = 220, 148
    image, draw = _card(width, height)

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
    """Next-class card with status and timing."""
    width, height = 220, 64
    image, draw = _card(width, height)

    _text(draw, 12, 8, t["next_title"], 14, INK)
    _badge(draw, width, 9, t["next_badge"])

    _surface(draw, 8, 30, width - 16, 26)
    _text(draw, 20, 33, t["course1"], 12, INK)
    _text(draw, 20, 46, t["next_line"], 9, MUTED)
    return image


def reminder_preview(t: dict) -> Image.Image:
    """Preview two upcoming course reminders."""
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


def _pin(draw: ImageDraw.ImageDraw, x: float, y: float, size: float, fill) -> None:
    """Rounded push pin outline from widget_ic_pin, sampled on its 24-unit grid."""
    points = [(7, 2), (17, 2), (17, 4), (16, 4), (16, 9), (19, 12), (19, 14), (13, 14), (13, 21),
              (12, 22), (11, 21), (11, 14), (5, 14), (5, 12), (8, 9), (8, 4), (7, 4)]
    k = size / 24
    draw.polygon([(_px(x + px * k), _px(y + py * k)) for px, py in points], fill=fill)


def memo_preview(locale: str) -> Image.Image:
    """Notebook checklist preview with pin, priority marker and notebook color like the live rows."""
    width, height = 300, 148
    image, draw = _card(width, height)
    texts = {
        "": ("笔记待办", "整理实验报告", "数据结构 · 第三次实验", "复习第三章", "其他笔记 · 本周计划", "2 项待办"),
        "en": ("Note to-dos", "Finish the lab report", "Data structures · Lab 3", "Review chapter 3", "Other notes · This week", "2 to-dos"),
        "zh-rTW": ("筆記待辦", "整理實驗報告", "資料結構 · 第三次實驗", "複習第三章", "其他筆記 · 本週計劃", "2 項待辦"),
    }[locale]
    heading, task1, meta1, task2, meta2, count = texts
    accent = (63, 162, 119, 255)
    rule = (194, 216, 204, 255)
    high = (186, 26, 26, 255)        # MEMO_PRIORITY_HIGH
    notebook = (44, 85, 135, 255)    # course palette onContainer
    draw.rounded_rectangle((_px(13), _px(27), _px(29), _px(45)), radius=_px(2), outline=accent, width=_px(1.5))
    draw.line((_px(18), _px(27), _px(18), _px(45)), fill=accent, width=_px(1))
    for y in (33, 38):
        draw.line((_px(22), _px(y), _px(26), _px(y)), fill=accent, width=_px(1))
    _text(draw, 41, 27, heading, 16, INK)
    count_width = draw.textlength(count, font=_font(11)) / SCALE
    _text(draw, width - 68 - count_width, 30, count, 11, accent)
    draw.ellipse((_px(width-60), _px(12), _px(width-12), _px(60)), fill=(243, 244, 244, 255))
    draw.line((_px(width-43), _px(36), _px(width-29), _px(36)), fill=accent, width=_px(2))
    draw.line((_px(width-36), _px(29), _px(width-36), _px(43)), fill=accent, width=_px(2))
    rows = ((64, task1, meta1, high, True, "High" if locale == "en" else "高", notebook),)
    for y, title, meta, box, pinned, marker, dot in rows:
        draw.ellipse(_square_bounds(26, y + 14, 20), outline=accent, width=_px(1.5))
        draw.rounded_rectangle((_px(5), _px(y + 6), _px(8), _px(y + 42)), radius=_px(1.5), fill=box)
        x = 68
        if pinned:
            _pin(draw, x, y + 8, 12, accent)
            x += 15
        _text(draw, x, y+7, title, 13, INK)
        meta_x = 68
        if marker:
            badge_width = draw.textlength(marker, font=_font(10)) / SCALE + 12
            draw.rounded_rectangle((_px(meta_x), _px(y + 22), _px(meta_x + badge_width), _px(y + 38)),
                radius=_px(8), fill=(206, 223, 214, 255))
            _text(draw, meta_x + 6, y + 24, marker, 10, box)
            meta_x += badge_width + 6
        if dot:
            draw.ellipse(_square_bounds(meta_x, y + 27, 7), fill=dot)
            meta_x += 12
        _text(draw, meta_x, y+24, meta, 10, MUTED)
        draw.line((_px(42), _px(y+76), _px(width-12), _px(y+76)), fill=rule, width=_px(.7))
    return image


def tasks_preview(locale: str) -> Image.Image:
    """Generic container preview; actual UI ships in the selected component."""
    width, height = 300, 148
    image, draw = _card(width, height)
    heading, caption, detail = {
        "": ("组件小组件", "由已安装的组件提供", "名称与界面随组件更新"),
        "en": ("Component widgets", "Provided by installed components", "Names and UI come from the owner"),
        "zh-rTW": ("組件小組件", "由已安裝的組件提供", "名稱與介面隨組件更新"),
    }[locale]
    _text(draw, 14, 10, heading, 14, INK)
    draw.rounded_rectangle((_px(14), _px(40), _px(width - 14), _px(height - 14)), radius=_px(12), outline=MUTED, width=_px(1))
    _text(draw, 26, 58, caption, 12, INK)
    _text(draw, 26, 87, detail, 10, MUTED)
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
            "widget_preview_memo.png": memo_preview(locale),
            "widget_preview_tasks.png": tasks_preview(locale),
        }
        for name, image in previews.items():
            path = out_dir / name
            image.save(path, format="PNG", optimize=True)
            print(f"wrote {out_dir.name}/{name} ({image.width}x{image.height})")


if __name__ == "__main__":
    main()
