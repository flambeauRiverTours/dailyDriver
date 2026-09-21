using System.Globalization;
using FluentAssertions;
using Notes.Desktop.ViewModels;

namespace Notes.Desktop.Tests.ViewModelTests
{
    /// <summary>
    /// <see cref="NoteSummary.ToUpdatedDisplay"/> is the one piece of the note list that is a pure
    /// function, so it gets tested as one — no database, no ViewModel, no factory.
    ///
    /// It lives here rather than in a value converter on purpose. How a timestamp reads is a
    /// decision, and a converter would put that decision in the view where nothing can reach it.
    /// </summary>
    public class NoteSummaryTests
    {
        [Fact]
        public void ToUpdatedDisplay_ForToday_ShowsTheTimeOnly()
        {
            var thisMorning = DateTimeOffset.Now.Date.AddHours(9).AddMinutes(5);

            // Within today, the date is noise — every row would repeat it.
            NoteSummary.ToUpdatedDisplay(new DateTimeOffset(thisMorning))
                       .Should().Be("09:05");
        }

        [Fact]
        public void ToUpdatedDisplay_EarlierThisYear_ShowsDayAndMonthWithoutAYear()
        {
            var earlier = DateTimeOffset.Now.Date.AddDays(-40);

            var display = NoteSummary.ToUpdatedDisplay(new DateTimeOffset(earlier));

            display.Should().Be(earlier.ToString("d MMM", CultureInfo.InvariantCulture));
            display.Should().NotContain(earlier.Year.ToString());
        }

        [Fact]
        public void ToUpdatedDisplay_InAPriorYear_IncludesTheYear()
        {
            var longAgo = new DateTimeOffset(2019, 3, 7, 14, 30, 0, TimeSpan.Zero);

            // Without the year, a note from 2019 and one from last March read identically.
            NoteSummary.ToUpdatedDisplay(longAgo.ToLocalTime())
                       .Should().Be("7 Mar 2019");
        }

        [Fact]
        public void ToUpdatedDisplay_UsesAnInvariantMonthNameRatherThanTheMachineCulture()
        {
            var march = new DateTimeOffset(2019, 3, 7, 14, 30, 0, TimeSpan.Zero);

            // Same reasoning as the editor's blank-title stamp: a list that reads "7 Mrz" on one
            // machine and "7 Mar" on another is the same data presented two ways for no reason.
            NoteSummary.ToUpdatedDisplay(march.ToLocalTime()).Should().Contain("Mar");
        }

        [Fact]
        public void ToUpdatedDisplay_ConvertsFromUtcToLocalTime()
        {
            // Timestamps are stored and compared in UTC. A note written at 23:30 local under a
            // positive offset is "tomorrow" in UTC, and would list under the wrong day if the
            // conversion were skipped.
            var utc = DateTimeOffset.UtcNow;

            var display = NoteSummary.ToUpdatedDisplay(utc);

            display.Should().Be(NoteSummary.ToUpdatedDisplay(utc.ToLocalTime()));
        }
    }
}
