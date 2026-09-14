using System.Globalization;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Notes.Desktop.EntityFramework.Model;
using Notes.Desktop.Tests.Helpers;
using Notes.Desktop.ViewModels;

namespace Notes.Desktop.Tests.ViewModelTests
{
    /// <summary>
    /// Covers <see cref="NoteEditorViewModel"/> through its public surface only — the bound
    /// properties and the generated commands — per the MVVM testing rule in <c>desktop/CLAUDE.md</c>.
    /// No <c>Window</c>, no <c>Dispatcher</c>, nothing from <c>System.Windows</c>: if any of that were
    /// needed here, the ViewModel would have crossed a boundary.
    ///
    /// The factory is a real SQLite database rather than a stub because the save path's whole job is
    /// deciding what reaches storage, and a mock would only ever confirm that the ViewModel called
    /// the method the test author expected it to call.
    ///
    /// **On the debounce:** every assignment to <c>Title</c> or <c>Body</c> schedules an autosave
    /// three seconds out. These tests drive the save command directly and finish long before that
    /// fires; the orphaned timer resolves against a disposed factory and is swallowed by the
    /// ViewModel's own catch, so it cannot fail a later test. Timing behaviour itself is untested
    /// here — see the note at the bottom of the file.
    /// </summary>
    public class NoteEditorViewModelTests
    {
        private const string TitleStampFormat = "ddd d MMM ' - ' HH':'mm";

        private static NoteEditorViewModel NewViewModel(InMemoryDbContextFactory factory) =>
            new(factory, NullLogger<NoteEditorViewModel>.Instance);

        private static CancellationToken Ct => TestContext.Current.CancellationToken;

        private static Task SaveAsync(NoteEditorViewModel viewModel) =>
            viewModel.SaveCommand.ExecuteAsync(null);

        #region The dirty guard

        [Fact]
        public void TypingInTheBody_MarksTheViewModelDirty()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            viewModel.IsDirty.Should().BeFalse();
            viewModel.Body = "a thought";
            viewModel.IsDirty.Should().BeTrue();
        }

        [Fact]
        public void TypingInTheTitle_MarksTheViewModelDirty()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            viewModel.Title = "a heading";
            viewModel.IsDirty.Should().BeTrue();
        }

        [Fact]
        public async Task Save_ClearsIsDirty()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = "a thought";

            await SaveAsync(viewModel);

            viewModel.IsDirty.Should().BeFalse();
        }

        [Fact]
        public async Task Save_WhenNothingHasChanged_WritesNothing()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            // Never touched, so never dirty. Without this guard the autosave timer would re-write an
            // untouched note every three seconds and re-queue it for sync each time.
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            (await read.Notes.AnyAsync(Ct)).Should().BeFalse();
        }

        [Fact]
        public async Task Save_TwiceWithNoEditBetween_DoesNotTouchTheNoteAgain()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = "a thought";
            await SaveAsync(viewModel);

            DateTimeOffset firstWrite;
            using (var read = factory.CreateDbContext())
            {
                firstWrite = (await read.Notes.SingleAsync(Ct)).UpdatedAt;
            }

            await SaveAsync(viewModel);

            using var verify = factory.CreateDbContext();
            var note = await verify.Notes.SingleAsync(Ct);

            // A no-op save must not bump UpdatedAt: that timestamp orders the note list and feeds
            // the sync comparison, so churning it would reshuffle the list for nothing.
            note.UpdatedAt.Should().Be(firstWrite);
        }

        #endregion

        #region The blank-body guard

        [Theory]
        [InlineData("")]
        [InlineData("   ")]
        [InlineData("\r\n")]
        [InlineData("\t \n ")]
        public async Task Save_WithAWhitespaceOnlyBody_WritesNothing(string body)
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = body;

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();

            // Opening the editor and closing it again must not leave an empty note behind. The
            // check is IsNullOrWhiteSpace, not IsNullOrEmpty, so a stray newline counts as empty.
            (await read.Notes.AnyAsync(Ct)).Should().BeFalse();
        }

        [Fact]
        public async Task Save_WithABlankBodyButATitle_StillWritesNothing()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Title = "a heading and nothing else";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();

            // The body is what a note is. A title alone is not a note worth keeping.
            (await read.Notes.AnyAsync(Ct)).Should().BeFalse();
        }

        #endregion

        #region The blank-title stamp

        [Fact]
        public async Task Save_WithNoTitle_StampsTheCreationTime()
        {
            var before = DateTimeOffset.UtcNow;
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            var after = DateTimeOffset.UtcNow;

            viewModel.Body = "untitled thought";
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // createdAt is stamped in the ViewModel's constructor and cannot be injected, so the
            // assertion brackets it: the title is the format applied to some instant inside the
            // window the constructor ran in. Minute resolution makes this two candidates at worst.
            note.Title.Should().BeOneOf(
                before.ToString(TitleStampFormat, CultureInfo.InvariantCulture),
                after.ToString(TitleStampFormat, CultureInfo.InvariantCulture));
        }

        [Fact]
        public async Task Save_WithNoTitle_UsesAnInvariantFormatRegardlessOfMachineCulture()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = "untitled thought";
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // Parsing it back under a culture that formats dates nothing like the invariant one
            // proves the stamp does not silently follow the machine. A note titled "Mo 14 Sep" on a
            // German desktop and "Mon 14 Sep" on an English one is the same note listed two ways.
            DateTimeOffset.TryParseExact(
                note.Title,
                TitleStampFormat,
                new CultureInfo("de-DE"),
                DateTimeStyles.None,
                out _).Should().BeFalse("the stamp is invariant, so a de-DE parse of it should not succeed");

            DateTimeOffset.TryParseExact(
                note.Title,
                TitleStampFormat,
                CultureInfo.InvariantCulture,
                DateTimeStyles.None,
                out var parsed).Should().BeTrue();

            // Format carries no year, so the parse defaults to the current one — day and month are
            // the parts worth checking.
            parsed.Date.Should().Be(DateTime.UtcNow.Date);
        }

        [Fact]
        public async Task Save_WithATitle_KeepsWhatTheUserTyped()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Title = "Sprint planning";
            viewModel.Body = "untitled thought";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            (await read.Notes.SingleAsync(Ct)).Title.Should().Be("Sprint planning");
        }

        [Fact]
        public async Task Save_WithAWhitespaceOnlyTitle_FallsBackToTheStamp()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Title = "   ";
            viewModel.Body = "untitled thought";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // Deleting a title down to spaces is the user clearing it, not naming it "   ".
            note.Title.Should().NotBe("   ");
            note.Title.Should().NotBeNullOrWhiteSpace();
        }

        [Fact]
        public async Task Save_OnUpdate_WithNoTitle_KeepsTheStampedTitle()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            viewModel.Body = "untitled thought";
            await SaveAsync(viewModel);

            string stamped;
            using (var read = factory.CreateDbContext())
            {
                stamped = (await read.Notes.SingleAsync(Ct)).Title;
            }

            viewModel.Body = "untitled thought, revised";
            await SaveAsync(viewModel);

            using var verify = factory.CreateDbContext();

            // The insert branch substitutes the stamp for a blank title; the update branch assigns
            // Title straight through. An untitled note therefore saves once and then writes null
            // over its own stamp — and because that save runs inside the fire-and-forget autosave,
            // the DbUpdateException is caught and logged rather than surfaced. The visible symptom
            // is a note that silently stops persisting three seconds after it is created.
            (await verify.Notes.SingleAsync(Ct)).Title.Should().Be(stamped);
        }

        [Fact]
        public async Task Save_OnUpdate_WhenTheTitleIsClearedToBlank_FallsBackToTheStamp()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Title = "Sprint planning";
            viewModel.Body = "a thought";
            await SaveAsync(viewModel);

            viewModel.Title = "   ";
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // Clearing a title should re-stamp, exactly as it does on insert. Whatever rule the
            // insert branch applies, the update branch has to apply the same one — two branches
            // disagreeing about what a blank title means is how a note ends up untitled in the list.
            note.Title.Should().NotBeNullOrWhiteSpace();
        }

        #endregion

        #region Line endings

        [Fact]
        public async Task Save_OnInsert_NormalisesWindowsLineEndings()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            // What a WPF TextBox actually produces when the user presses Enter.
            viewModel.Body = "first line\r\nsecond line\r\n- [ ] a task @2026-09-20";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // Bodies are stored LF-only. Android writes LF, the parsers split on '\n', and the wire
            // format is shared — a CRLF body would round-trip through sync as a different string
            // than the one the other client wrote, and diff as changed on every pull.
            note.Body.Should().NotContain("\r");
            note.Body.Should().Be("first line\nsecond line\n- [ ] a task @2026-09-20");
        }

        [Fact]
        public async Task Save_OnUpdate_NormalisesWindowsLineEndings()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            // A title, only so this test fails for line-ending reasons and nothing else — the
            // update branch cannot currently survive a null one. See
            // Save_OnUpdate_WithNoTitle_KeepsTheStampedTitle below.
            viewModel.Title = "a heading";
            viewModel.Body = "first line";
            await SaveAsync(viewModel);

            // The second save goes down the update branch, which is the path an autosave takes for
            // every keystroke after the first three seconds — so it is the common case, not the
            // edge one.
            viewModel.Body = "first line\r\nsecond line";
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            note.Body.Should().NotContain("\r");
            note.Body.Should().Be("first line\nsecond line");
        }

        [Fact]
        public async Task Load_PresentsTheBodyWithWindowsLineEndings()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = Guid.NewGuid();

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(new Note
                {
                    Id = id,
                    Title = "from the server",
                    Body = "first line\nsecond line",
                    CreatedAt = DateTimeOffset.UtcNow,
                    UpdatedAt = DateTimeOffset.UtcNow,
                    SourceDevice = "Android"
                });
                await write.SaveChangesAsync(Ct);
            }

            var viewModel = NewViewModel(factory);
            await viewModel.LoadCommand.ExecuteAsync(id);

            // The inverse of the save-path normalisation: LF on disk, CRLF in the TextBox. WPF's
            // TextBox renders a lone LF as a line break but then re-emits CRLF on the next edit, so
            // converting on the way in keeps the editor's own idea of the text self-consistent.
            viewModel.Body.Should().Be("first line\r\nsecond line");
            viewModel.Title.Should().Be("from the server");
        }

        #endregion

        #region Load

        [Fact]
        public async Task Load_MakesTheViewModelSaveBackToTheSameNote()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = Guid.NewGuid();
            var createdAt = DateTimeOffset.UtcNow.AddDays(-3);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(new Note
                {
                    Id = id,
                    Title = "existing",
                    Body = "original body",
                    CreatedAt = createdAt,
                    UpdatedAt = createdAt,
                    Rev = 7,
                    SourceDevice = "Android"
                });
                await write.SaveChangesAsync(Ct);
            }

            var viewModel = NewViewModel(factory);
            await viewModel.LoadCommand.ExecuteAsync(id);
            viewModel.Body = "edited body";
            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var notes = await read.Notes.ToListAsync(Ct);

            // One note, not two. A save that forgot the loaded id would insert under the ViewModel's
            // own freshly-generated Guid and silently fork the note.
            notes.Should().ContainSingle();
            notes[0].Id.Should().Be(id);
            notes[0].Body.Should().Be("edited body");
            notes[0].CreatedAt.Should().BeCloseTo(createdAt, TimeSpan.FromMilliseconds(1));
            notes[0].Rev.Should().Be(7, "a local edit does not advance the server's revision");
        }

        [Fact]
        public async Task Load_WithAnUnknownId_Throws()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            var act = () => viewModel.LoadCommand.ExecuteAsync(Guid.NewGuid());

            await act.Should().ThrowAsync<InvalidOperationException>();
        }

        #endregion

        #region The editor's seam with derive-on-save

        [Fact]
        public async Task Save_DerivesTagsAndQueuesTheNoteForSync()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = "standup notes #work";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(Ct);

            // Covered exhaustively in SaveChangesTests; asserted once here to show the editor
            // reaches that machinery rather than bypassing it with its own SQL.
            (await read.NoteTags.Where(t => t.NoteId == note.Id).Select(t => t.Tag).ToListAsync(Ct))
                .Should().BeEquivalentTo(["work"]);
            (await read.Outboxes.AnyAsync(o => o.NoteId == note.Id, Ct)).Should().BeTrue();
        }

        [Fact]
        public async Task Save_AcrossManyEdits_LeavesOneOutboxRow()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Title = "a heading"; // see the note in Save_OnUpdate_NormalisesWindowsLineEndings

            foreach (var body in new[] { "draft one", "draft two", "draft three", "draft four" })
            {
                viewModel.Body = body;
                await SaveAsync(viewModel);
            }

            using var read = factory.CreateDbContext();

            // This is the autosave scenario in miniature: four saves of one note, one queued push.
            (await read.Outboxes.CountAsync(Ct)).Should().Be(1);
            (await read.Notes.SingleAsync(Ct)).Body.Should().Be("draft four");
        }

        [Fact]
        public async Task Save_DerivesNoTasks_BecauseTheEditorCannotYetAuthorAChecklist()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);
            viewModel.Body = "- [ ] ship the thing @2026-09-20";

            await SaveAsync(viewModel);

            using var read = factory.CreateDbContext();

            // Not a bug, and not an endorsement either — a record of where T057 stops. The ViewModel
            // hard-codes NoteType.Note and TaskIndex derives tasks only from a Checklist, so typing
            // a dated checkbox into the editor today produces no task row. T058 is what gives the
            // editor a way to set the type; when it does, this test should start failing and be
            // rewritten rather than deleted.
            (await read.Tasks.AnyAsync(Ct)).Should().BeFalse();
            (await read.Notes.SingleAsync(Ct)).Type.Should().Be(Notes.Shared.Model.NoteType.Note);
        }

        #endregion

        // Not covered here, deliberately:
        //
        //   * **Autosave timing.** Asserting that a save lands three seconds after a keystroke means
        //     a three-second test, and asserting that it does NOT land earlier means another. The
        //     delay is a const with no seam to shorten it, and adding one is a production change.
        //     Worth raising as a design question — an injected delay would make both testable — but
        //     not worth eight seconds of wall clock on every run in the meantime.
        //
        //   * **Debounce coalescing.** That a burst of keystrokes produces one save rather than N
        //     has the same problem, plus it is the behaviour most likely to regress quietly. Same
        //     seam, same answer.
    }
}
