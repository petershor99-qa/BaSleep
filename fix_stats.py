import io

p = 'app/src/main/java/com/family/sleepschedule/MainActivity.kt'
s = io.open(p, encoding='utf-8', newline=None).read()

# 1. Нумерация ВСЕХ сегментов сна (факт + план, сквозная) + метки времени под ними
old_num = '''                                    Row(Modifier.fillMaxSize()) {
                                        var napN = 0
                                        tl.segments.forEachIndexed { i, seg ->
                                            val isLive = i == tl.liveIndex
                                            val w = seg.dur.toFloat().coerceAtLeast(1f)
                                            val bg = when {
                                                seg.isSleep && seg.isFact -> NightTint
                                                seg.isSleep -> NightTint.copy(alpha = 0.40f)
                                                seg.isFact -> DarkScheme.surfaceVariant
                                                else -> MutedBg
                                            }
                                            Box(
                                                Modifier
                                                    .weight(w)
                                                    .fillMaxSize()
                                                    .background(bg)
                                                    .border(
                                                        1.dp,
                                                        if (isLive) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                                                        RoundedCornerShape(4.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (seg.isSleep && seg.isFact) {
                                                    napN++
                                                    if (barMaxWidth * (w / tl.endRel) >= 34.dp) {
                                                        Text("$napN", fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxSize()) {
                                        val left = tl.markerFrac.coerceIn(0.002f, 0.998f)
                                        Spacer(Modifier.weight(left))
                                        Box(Modifier.width(3.dp).fillMaxSize().background(Color.White.copy(alpha = 0.85f)))
                                        Spacer(Modifier.weight(1f - left))
                                    }
                            }'''
new_num = '''                                    Row(Modifier.fillMaxSize()) {
                                        var sleepN = 0
                                        tl.segments.forEachIndexed { i, seg ->
                                            val isLive = i == tl.liveIndex
                                            val w = seg.dur.toFloat().coerceAtLeast(1f)
                                            val bg = when {
                                                seg.isSleep && seg.isFact -> NightTint
                                                seg.isSleep -> NightTint.copy(alpha = 0.40f)
                                                seg.isFact -> DarkScheme.surfaceVariant
                                                else -> MutedBg
                                            }
                                            if (seg.isSleep) sleepN++
                                            Box(
                                                Modifier
                                                    .weight(w)
                                                    .fillMaxSize()
                                                    .background(bg)
                                                    .border(
                                                        1.dp,
                                                        if (isLive) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                                                        RoundedCornerShape(4.dp),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (seg.isSleep && barMaxWidth * (w / tl.endRel) >= 34.dp) {
                                                    Text("$sleepN", fontSize = 9.sp, color = Color.White.copy(alpha = 0.9f))
                                                }
                                            }
                                        }
                                    }
                                    // Метки времени начала каждого сна под фиолетовыми кусками
                                    Row(Modifier.fillMaxSize()) {
                                        tl.segments.forEach { seg ->
                                            Box(Modifier.weight(seg.dur.toFloat().coerceAtLeast(1f)), contentAlignment = Alignment.Center) {
                                                if (seg.isSleep) {
                                                    Text(
                                                        clock(state.dayStart + seg.relStart * LiveTracker.MINUTE),
                                                        fontSize = 9.sp, color = NightTint, style = Tabular,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxSize()) {
                                        val left = tl.markerFrac.coerceIn(0.002f, 0.998f)
                                        Spacer(Modifier.weight(left))
                                        Box(Modifier.width(3.dp).fillMaxSize().background(Color.White.copy(alpha = 0.85f)))
                                        Spacer(Modifier.weight(1f - left))
                                    }
                            }'''
assert old_num in s, 'bar block not found'
s = s.replace(old_num, new_num, 1)

# 2. Статистика дня — всегда видна: плитки + строка бодрствования
old_fact = '''                            // Факт дня — сворачиваемый блок
                            var factsOpen by rememberSaveable { mutableStateOf(false) }
                            val t = History.totals(history, state, now)
                            // Ночью: идущая сейчас ночь, иначе последняя завершённая (или «—»)
                            val liveNight = state.phase == LiveTracker.Phase.NIGHT
                            val nightMinutes = if (liveNight) SleepEngine.nightSleepMinutes(history, state, now)
                                else History.days(history).firstOrNull()?.night?.minutes
                            val nightLabel = when {
                                liveNight -> "Ночью сейчас"
                                nightMinutes != null -> "Прошлой ночью"
                                else -> "Ночью"
                            }
                            val nightText = nightMinutes?.let { SleepEngine.fmtDurHuman(it) } ?: "—"
                            val totalMin = t.daySleepMin + (nightMinutes ?: 0)
                            Row(
                                Modifier.fillMaxWidth().clickable { factsOpen = !factsOpen }.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Факт", fontSize = 12.sp, color = Subtitle)
                                Text(
                                    "днём ${SleepEngine.fmtDurHuman(t.daySleepMin)} · всего ${SleepEngine.fmtDurHuman(totalMin)}",
                                    fontSize = 12.sp, color = Subtitle, style = Tabular,
                                )
                                Text(if (factsOpen) "▾" else "▸", fontSize = 12.sp, color = Subtitle)
                            }
                            if (factsOpen) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FactTile("Днём сегодня", SleepEngine.fmtDurHuman(t.daySleepMin), age.dayNormShort, t.daySleepMin >= age.dayMax - 15, Modifier.weight(1f))
                                    FactTile(nightLabel, nightText, age.nightNormShort, (nightMinutes ?: 0) >= age.nightMax - 15, Modifier.weight(1f))
                                    FactTile("Всего за сутки", SleepEngine.fmtDurHuman(totalMin), age.totalNormShort, totalMin >= age.totalMax - 15, Modifier.weight(1f))
                                }
                                t.dayAwakeMin?.let {
                                    Text("Бодрствование днём ${SleepEngine.fmtDurHuman(it)}", fontSize = 12.sp, color = Subtitle)
                                }
                            }'''
new_fact = '''                            // Статистика дня — всегда видна (§запрос v1.19.1)
                            val t = History.totals(history, state, now)
                            val liveNight = state.phase == LiveTracker.Phase.NIGHT
                            val nightMinutes = if (liveNight) SleepEngine.nightSleepMinutes(history, state, now)
                                else History.days(history).firstOrNull()?.night?.minutes
                            val nightLabel = when {
                                liveNight -> "Ночью сейчас"
                                nightMinutes != null -> "Прошлой ночью"
                                else -> "Ночью"
                            }
                            val nightText = nightMinutes?.let { SleepEngine.fmtDurHuman(it) } ?: "—"
                            val totalMin = t.daySleepMin + (nightMinutes ?: 0)
                            val nightWakeMin = History.days(history).firstOrNull()?.nightWakeMin ?: 0
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FactTile("Сон днём", SleepEngine.fmtDurHuman(t.daySleepMin), age.dayNormShort, t.daySleepMin >= age.dayMax - 15, Modifier.weight(1f))
                                FactTile(nightLabel, nightText, age.nightNormShort, (nightMinutes ?: 0) >= age.nightMax - 15, Modifier.weight(1f))
                                FactTile("Сон за сутки", SleepEngine.fmtDurHuman(totalMin), age.totalNormShort, totalMin >= age.totalMax - 15, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("⏱ Бодрствовал", fontSize = 13.sp, color = Subtitle)
                                Text(
                                    "днём ${SleepEngine.fmtDurHuman(t.dayAwakeMin ?: 0)}" +
                                        if (nightWakeMin > 0) " · ночью ${SleepEngine.fmtDurHuman(nightWakeMin)}" else "",
                                    fontSize = 13.sp, color = DarkScheme.onSurface, style = Tabular,
                                )
                            }'''
assert old_fact in s, 'fact block not found'
s = s.replace(old_fact, new_fact, 1)

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('OK')
