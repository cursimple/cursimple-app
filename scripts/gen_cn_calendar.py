#!/usr/bin/env python3
"""生成 data/calendar/cn-festivals.json：每一天是什么节日、什么节气。

App 联网时静默下载这份文件，节日问候按日期直接查，不在代码里写死日期。
农历与节气用寿星天文历（sxtwl）算：pip install sxtwl
用法：python3 scripts/gen_cn_calendar.py [起始年] [结束年]
"""
import datetime
import json
import sys

import sxtwl

FIRST_YEAR = int(sys.argv[1]) if len(sys.argv) > 1 else 2024
LAST_YEAR = int(sys.argv[2]) if len(sys.argv) > 2 else 2060

# sxtwl 的节气序号从冬至开始
TERMS = [
    "winter_solstice", "minor_cold", "major_cold", "start_of_spring", "rain_water", "awakening_of_insects",
    "spring_equinox", "pure_brightness", "grain_rain", "start_of_summer", "grain_buds", "grain_in_ear",
    "summer_solstice", "minor_heat", "major_heat", "start_of_autumn", "end_of_heat", "white_dew",
    "autumn_equinox", "cold_dew", "frost_descent", "start_of_winter", "minor_snow", "major_snow",
]
SOLAR = {
    (1, 1): "new_year", (2, 14): "valentine", (3, 8): "womens_day", (3, 12): "arbor_day",
    (4, 1): "april_fools", (5, 1): "labor_day", (5, 4): "youth_day", (6, 1): "childrens_day",
    (9, 10): "teachers_day", (10, 1): "national_day", (12, 24): "christmas_eve", (12, 25): "christmas",
}
LUNAR = {
    (1, 1): "spring_festival", (1, 15): "lantern", (2, 2): "dragon_head", (5, 5): "dragon_boat",
    (7, 7): "qixi", (8, 15): "mid_autumn", (9, 9): "double_ninth", (12, 8): "laba",
}


def nth_sunday(year, month, n):
    day = datetime.date(year, month, 1)
    day += datetime.timedelta(days=(6 - day.weekday()) % 7)
    return day + datetime.timedelta(weeks=n - 1)


def lunar(day):
    d = sxtwl.fromSolar(day.year, day.month, day.day)
    return d, (d.getLunarMonth(), d.getLunarDay()), bool(d.isLunarLeap())


days = {}
day = datetime.date(FIRST_YEAR, 1, 1)
end = datetime.date(LAST_YEAR, 12, 31)
while day <= end:
    ids = []
    if (day.month, day.day) in SOLAR:
        ids.append(SOLAR[(day.month, day.day)])
    if day == nth_sunday(day.year, 5, 2):
        ids.append("mothers_day")
    if day == nth_sunday(day.year, 6, 3):
        ids.append("fathers_day")
    d, md, leap = lunar(day)
    if not leap and md in LUNAR:
        ids.append(LUNAR[md])
    # 除夕是正月初一的前一天：腊月有大小月，按「明天是不是初一」认
    _, tomorrow, tomorrow_leap = lunar(day + datetime.timedelta(days=1))
    if not tomorrow_leap and tomorrow == (1, 1):
        ids.append("new_years_eve")
    if d.hasJieQi():
        ids.append(TERMS[d.getJieQi()])
    if ids:
        days[day.isoformat()] = ids
    day += datetime.timedelta(days=1)

dataset = {
    "version": 1,
    "firstYear": FIRST_YEAR,
    "lastYear": LAST_YEAR,
    "days": days,
}
with open("data/calendar/cn-festivals.json", "w", encoding="utf-8") as f:
    json.dump(dataset, f, ensure_ascii=False, separators=(",", ":"))
    f.write("\n")
print(f"{len(days)} days, {FIRST_YEAR}-{LAST_YEAR}")
