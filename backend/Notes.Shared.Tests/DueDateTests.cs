using FluentAssertions;
using Notes.Shared.Model;
using Notes.Shared.Tests.Helpers.Fixtures;

namespace Notes.Shared.Tests
{
    /// <summary>
    /// The stored due-date format, pinned by <c>fixtures/due-date-parsing.json</c>.
    ///
    /// Every <c>storedCase</c> is a full checklist line, so these exercise
    /// <see cref="ChecklistParser.ParseChecklist"/> rather than a separate entry point — a due date
    /// is lifted out of an item *during* parsing, not by a parser bolted on beside it.
    /// </summary>
    public class DueDateTests
    {
        public static IEnumerable<object[]> StoredCases() =>
            DueDateFixture.Instance.StoredCases
                .Select(c => new object[] { c.Body, c.Text, c.Due!, c.Checked, c.Name });

        public static IEnumerable<object[]> RenderCases() =>
            DueDateFixture.Instance.RenderCases
                .Select(c => new object[] { c.Text, c.Due!, c.Checked, c.Body, c.Name });

        /// <summary>Bodies only — the round-trip property asserts against itself, not the expectations.</summary>
        public static IEnumerable<object[]> StoredBodies() =>
            DueDateFixture.Instance.StoredCases
                .Select(c => new object[] { c.Body, c.Name });

        [Theory]
        [MemberData(nameof(StoredCases))]
        public void ParseChecklist_LiftsTheDueDateOutOfTheItem(
            string body, string expectedText, DateOnly? expectedDue, bool expectedChecked, string caseName)
        {
            var items = ChecklistParser.ParseChecklist(body);

            items.Should().ContainSingle(because: caseName);
            items[0].text.Should().Be(expectedText, because: caseName);
            items[0].due.Should().Be(expectedDue, because: caseName);
            items[0].@checked.Should().Be(expectedChecked, because: caseName);
        }

        [Theory]
        [MemberData(nameof(RenderCases))]
        public void RenderChecklist_WritesTheDueTokenAtEndOfLine(
            string text, DateOnly? due, bool isChecked, string expectedBody, string caseName)
        {
            var item = new ChecklistItem { text = text, @checked = isChecked, due = due };

            ChecklistParser.RenderChecklist([item]).Should().Be(expectedBody, because: caseName);
        }

        /// <summary>
        /// Parse and render are an identity for anything parse produces — extended to dated items.
        /// The editor round-trips the whole body through both on every keystroke, so a line that
        /// does not survive this is a line that rewrites itself while you type.
        /// </summary>
        [Theory]
        [MemberData(nameof(StoredBodies))]
        public void ParseAndRender_AreAnIdentityForDatedItems(string body, string caseName)
        {
            var parsed = ChecklistParser.ParseChecklist(body);
            var reparsed = ChecklistParser.ParseChecklist(ChecklistParser.RenderChecklist(parsed));

            reparsed.Should().Equal(parsed, because: caseName);
        }

        /// <summary>
        /// Cleaning must preserve the due date. A dated item with empty text survives the filter
        /// (<c>text.isNotEmpty() || due != null</c>) — dropping its date instead would silently
        /// demote a task to an ordinary checklist item, and <c>Clean</c> is what a client applies
        /// before storing.
        ///
        /// Hand-written rather than fixture-driven: no <c>cleanCase</c> carries a due date, and none
        /// has a surviving *checked* item either, so the corpus cannot pin either rule.
        /// </summary>
        [Fact]
        public void Clean_PreservesDueDateAndCheckedState()
        {
            var items = new[]
            {
                new ChecklistItem { text = "renew passport ", @checked = true, due = new DateOnly(2026, 8, 25) },
                new ChecklistItem { text = "", @checked = false, due = new DateOnly(2026, 9, 1) },
                new ChecklistItem { text = "   ", @checked = false, due = null },
            };

            ChecklistParser.Clean(items).Should().Equal(
                new ChecklistItem { text = "renew passport", @checked = true, due = new DateOnly(2026, 8, 25) },
                new ChecklistItem { text = "", @checked = false, due = new DateOnly(2026, 9, 1) });
        }

        /// <summary>
        /// A dated prose line is promoted to a task on note→checklist conversion, so the split runs
        /// on the fallback branch too, not just on marker lines.
        /// </summary>
        [Fact]
        public void ParseChecklist_LiftsDueDatesOutOfProseLinesToo()
        {
            var items = ChecklistParser.ParseChecklist("renew passport @2026-08-25");

            items.Should().ContainSingle();
            items[0].text.Should().Be("renew passport");
            items[0].due.Should().Be(new DateOnly(2026, 8, 25));
            items[0].@checked.Should().BeFalse();
        }

        [Fact]
        public void StoredCases_AreAllLoaded() =>
            DueDateFixture.Instance.StoredCases.Should().HaveCount(12);

        [Fact]
        public void RenderCases_AreAllLoaded() =>
            DueDateFixture.Instance.RenderCases.Should().HaveCount(3);

        /// <summary>
        /// Phase 6 has no implementation yet; this asserts the corpus is loadable so a truncated
        /// fixture fails now rather than when the shortcut resolver is written.
        /// </summary>
        [Fact]
        public void InputCases_AreAllLoaded() =>
            DueDateFixture.Instance.InputCases.Should().HaveCount(21);
    }
}
