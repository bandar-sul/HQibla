package sa.h9.qibla;

import static org.junit.Assert.assertArrayEquals;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class PrayerTimesCalculatorTest {
 @Test public void buraydahReferenceDate() {
  Calendar date = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00"), Locale.US);
  date.clear(); date.set(2026, Calendar.OCTOBER, 14, 12, 0, 0);
  PrayerTimesCalculator.Result result = PrayerTimesCalculator.calculate(26.326, 43.975, date);
  assertArrayEquals(new String[] {"04:45", "06:02", "11:51", "15:10", "17:38", "19:08"}, result.times24);
 }
}
