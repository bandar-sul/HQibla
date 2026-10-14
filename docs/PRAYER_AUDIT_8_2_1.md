# H9 Qibla 8.2.1 — Prayer-time audit

## Reference sample
- Date: 14 October 2026
- Location: Buraydah, Saudi Arabia
- Test coordinates: 26.326 N, 43.975 E
- Time zone: Saudi Arabia (UTC+03:00)
- Reference: Umm Al-Qura Calendar website, city table for Buraydah.

## Comparison (local 24-hour time)

| Prayer | Umm al-Qura reference | Version 8.2 before | 8.2.1 candidate | Difference after |
|---|---:|---:|---:|---:|
| Fajr | 04:45 | 04:45 | 04:45 | 0 min |
| Sunrise | 06:02 | 06:02 | 06:02 | 0 min |
| Dhuhr | 11:51 | 11:51 | 11:51 | 0 min |
| Asr | 15:10 | 15:09 | 15:10 | 0 min |
| Maghrib | 17:38 | 17:38 | 17:38 | 0 min |
| Isha | 19:08 | 19:08 | 19:08 | 0 min |

## Change made
The Asr result was one minute earlier than the published city-table value in this reference sample. A +1 minute Asr calibration was added, and a JUnit regression test was added for this date and location. Existing Fajr (+2 minutes), Dhuhr (+1 minute), Sunrise (0), and Isha (90 minutes outside Ramadan / 120 minutes during Ramadan) rules remain unchanged.

## Scope and limitation
This is a one-date regression check against the published city table, not proof that every date and every GPS coordinate matches to the minute. A broader multi-date / multi-city audit is still needed before claiming universal equivalence.
