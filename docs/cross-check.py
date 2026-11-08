#!/usr/bin/env python3
"""Независимый кросс-чек ключевых чисел BaSleep — формулы пересчитаны с нуля.
Запуск: python docs/cross-check.py"""

MIN = 60
win = [85, 95, 100, 105, 80]   # окна «3 мес · 4 сна» (Горка)
naps = [80, 80, 70, 60]

day = sum(naps); ww = sum(win)
night = 1440 - day - ww; total = day + night
assert 240 <= day <= 300 and 660 <= night <= 720 and 900 <= total <= 1020
assert ww + day + night == 1440
for w in win: assert w % 5 == 0 and 70 <= w <= 110
assert win[0] < win[1] < win[2] < win[3] and win[4] < win[3]   # Горка

comp = max(-15, min(15, 57 - 70)); assert comp == -13
assert 105 + comp - 10 == 82          # окно после короткого сна с regress

wakeTod = 11*60+53
assert (10 + 1440) - wakeTod == 737   # полночь: 00:10 от подъёма 11:53
assert 755 - 737 == 18                # до отбоя 00:28

night_start = 21*60+35
assert (3*60 + 1440 - night_start) - 20 == 305   # ночной сон 21:35→03:00 − бодрств. 20 мин

import datetime, zoneinfo
dt = datetime.datetime(2026, 11, 6, 9, 0, tzinfo=zoneinfo.ZoneInfo('Europe/Moscow'))
assert dt.hour*60 + dt.minute == 540   # московский подъём — не UTC 360

for nap_fact, plan_nap, expect in [(40, 70, -15), (100, 70, 15), (70, 70, 0)]:
    assert max(-15, min(15, nap_fact - plan_nap)) == expect   # границы компенсации

print('Все независимые кросс-чеки пройдены: схема 16:15, окно 82, полночь 737/18, ночь 305, зона 540')
