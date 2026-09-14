using System.Globalization;
using FluentAssertions;
using Notes.Shared.Model;

namespace Notes.Shared.Tests
{
    /// <summary>
    /// Derivation of the task index from a note body (ADR-0007, extended server-side by ADR-0008).
    ///
    /// **No fixture covers this.** `tasksOf` appears in neither `checklist-parsing.json` nor
    /// `due-date-parsing.json`, so every assertion here is hand-written against the Kotlin
    /// reference in `android/.../notes/TaskIndex.kt` rather than against a shared corpus. That makes
    /// this the one place in T042 where "the fixture is the spec" offers no protection.
    /// </summary>
    public class TaskIndexTests
    {
        private static Note NoteWith(NoteType type, string body, bool deleted = false) => new()
        {
            id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
            userId = "user123",
            type = type,
            title = "Shopping",
            body = body,
            tags = [],
            createdAt = new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero),
            updatedAt = new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero),
            deleted = deleted,
            sourceDevice = "desktop",
            rev = 7,
        };

        /// <summary>
        /// The guard that makes "a task is a dated checklist item" literally true. Prose parses as
        /// one item per line, so without this any sentence mentioning a date becomes a task nobody
        /// created.
        /// </summary>
        [Fact]
        public void TasksOf_ReturnsNothingForAProseNote_EvenWhenItMentionsADate() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Note, "renew passport @2026-08-25"))
                .Should().BeEmpty();

        [Fact]
        public void TasksOf_ReturnsNothingWhenNoItemIsDated() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] milk\n- [x] eggs"))
                .Should().BeEmpty();

        /// <summary>
        /// <b>Position is the index in the full checklist, not among the dated subset.</b> Kotlin
        /// uses <c>mapIndexedNotNull</c>, so the index comes from the enumeration *before* the date
        /// filter — which is what lets a UI tap on a task find the right line to tick. A
        /// <c>Where(...).Select((item, i) => …)</c> implementation renumbers from zero and passes
        /// every other test in this file.
        /// </summary>
        [Fact]
        public void TasksOf_NumbersPositionsAgainstTheFullChecklist()
        {
            var note = NoteWith(NoteType.Checklist,
                "- [ ] milk\n- [ ] renew passport @2026-08-25\n- [ ] eggs\n- [x] call the vet @2026-09-01");

            var tasks = TaskIndex.TasksOf(note);

            tasks.Should().HaveCount(2);
            tasks[0].position.Should().Be(1);
            tasks[1].position.Should().Be(3);
        }

        [Fact]
        public void TasksOf_CarriesTheCheckedStateThrough()
        {
            var note = NoteWith(NoteType.Checklist,
                "- [ ] renew passport @2026-08-25\n- [x] call the vet @2026-09-01");

            var tasks = TaskIndex.TasksOf(note);

            tasks[0].@checked.Should().BeFalse();
            tasks[1].@checked.Should().BeTrue();
        }

        /// <summary>
        /// The index is a query projection, not the live editing buffer, so the
        /// preserve-trailing-whitespace rule that governs <see cref="ChecklistParser.ParseChecklist"/>
        /// does not apply here. Same distinction as <c>Clean</c>.
        /// </summary>
        [Fact]
        public void TasksOf_TrimsTheItemText() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] renew passport  @2026-08-25"))
                .Single().text.Should().Be("renew passport");

        [Fact]
        public void TasksOf_CarriesTheNoteId() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] renew passport @2026-08-25"))
                .Single().noteId.Should().Be(Guid.Parse("11111111-1111-1111-1111-111111111111"));

        [Fact]
        public void TasksOf_FormatsDueAsIsoDate() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] renew passport @2026-08-25"))
                .Single().due.Should().Be("2026-08-25");

        /// <summary>
        /// <c>DateOnly.ToString()</c> with no arguments uses the <b>current culture's</b> short-date
        /// pattern — <c>25.08.2026</c> under <c>de-DE</c>, <c>8/25/2026</c> under <c>en-US</c>. Only
        /// an explicit <c>"yyyy-MM-dd"</c> with <see cref="CultureInfo.InvariantCulture"/> is safe.
        ///
        /// Asserted under a non-invariant culture deliberately: the same class of bug as
        /// <c>ToLower()</c> vs <c>ToLowerInvariant()</c> in <c>TagParser</c>, and one that otherwise
        /// only surfaces on someone else's machine.
        /// </summary>
        [Fact]
        public void TasksOf_FormatsDueAsIsoDateRegardlessOfCulture()
        {
            var original = CultureInfo.CurrentCulture;
            try
            {
                CultureInfo.CurrentCulture = new CultureInfo("de-DE");

                TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] renew passport @2026-08-25"))
                    .Single().due.Should().Be("2026-08-25");
            }
            finally
            {
                CultureInfo.CurrentCulture = original;
            }
        }

        /// <summary>
        /// A soft-deleted note still derives rows — they are excluded at *query* time instead, so
        /// undoing a delete needs no rebuild. A <c>deleted</c> guard here looks obviously right and
        /// would be a subtle bug.
        /// </summary>
        [Fact]
        public void TasksOf_StillDerivesRowsForASoftDeletedNote() =>
            TaskIndex.TasksOf(NoteWith(NoteType.Checklist, "- [ ] renew passport @2026-08-25", deleted: true))
                .Should().ContainSingle();

        /// <summary>Rows come back in body order; ordering and grouping are query concerns.</summary>
        [Fact]
        public void TasksOf_ReturnsRowsInBodyOrderNotDateOrder()
        {
            var note = NoteWith(NoteType.Checklist,
                "- [ ] later @2026-12-25\n- [ ] sooner @2026-08-25");

            TaskIndex.TasksOf(note).Select(t => t.text)
                .Should().Equal("later", "sooner");
        }
    }
}
