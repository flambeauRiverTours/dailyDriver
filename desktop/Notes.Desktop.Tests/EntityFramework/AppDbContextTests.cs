using FluentAssertions;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Notes.Desktop.EntityFramework;
using Notes.Desktop.EntityFramework.Model;

namespace Notes.Desktop.Tests.EntityFramework
{
    public class AppDbContextTests
    {
        #region Test for OnModelCreating
        [Fact()]
        public async Task AppContextDb_OnModelCreating_HardDeleteOfNoteCascadesToOtherEntities()
        {
            TestDbContext testDbContext = new TestDbContext();
            await testDbContext.Context.Database.MigrateAsync(TestContext.Current.CancellationToken);

            // The tag comes from the body, not from a hand-inserted row: SaveChanges re-derives
            // NoteTags on every write, so a tag that disagrees with the body is wiped by design.
            var note = new Note { Id = Guid.NewGuid(), Body = "standup notes #work" };
            testDbContext.Context.Notes.Add(note);
            await testDbContext.Context.SaveChangesAsync(TestContext.Current.CancellationToken);

            testDbContext.Context.Notes.Remove(note);
            await testDbContext.Context.SaveChangesAsync(TestContext.Current.CancellationToken);

            using var verifyContext = new AppDbContext(testDbContext.Options); // fresh context, same connection
            (await verifyContext.NoteTags.AnyAsync(t => t.NoteId == note.Id, TestContext.Current.CancellationToken)).Should().BeFalse();
            testDbContext.Dispose();
            verifyContext.Dispose();

        }

        [Fact()]
        public async Task AppContextDb_OnModelCreating_CompositeKeyRejectsDuplicate()
        {
            TestDbContext testDbContext = new TestDbContext();
            Guid noteId = Guid.NewGuid();
            var note = new Note { Id = noteId, /* ... */ };
            testDbContext.Context.Notes.Add(note);
            await testDbContext.Context.SaveChangesAsync(TestContext.Current.CancellationToken);
            using (var ctx1 = new AppDbContext(testDbContext.Options))
            {
                ctx1.NoteTags.Add(new NoteTag { NoteId = noteId, Tag = "work" });
                await ctx1.SaveChangesAsync(TestContext.Current.CancellationToken);
            }

            using (var ctx2 = new AppDbContext(testDbContext.Options))
            {
                ctx2.NoteTags.Add(new NoteTag { NoteId = noteId, Tag = "work" }); // same key, ctx2 has no idea
                Func<System.Threading.Tasks.Task> act = async () => await ctx2.SaveChangesAsync(CancellationToken.None);
                await act.Should().ThrowAsync<DbUpdateException>();
            }

            testDbContext.Dispose();
        }

        [Fact()]
        public async Task AppContextDb_OnModelCreating_SoftDeletDoesNotCascade()
        {
            TestDbContext testDbContext = new TestDbContext();
            await testDbContext.Context.Database.MigrateAsync(TestContext.Current.CancellationToken);

            // The tag comes from the body, not from a hand-inserted row: SaveChanges re-derives
            // NoteTags on every write, so a tag that disagrees with the body is wiped by design.
            var note = new Note { Id = Guid.NewGuid(), Body = "standup notes #work" };
            testDbContext.Context.Notes.Add(note);
            await testDbContext.Context.SaveChangesAsync(TestContext.Current.CancellationToken);

            note.Deleted = true;
            await testDbContext.Context.SaveChangesAsync(TestContext.Current.CancellationToken);

            using var verifyContext = new AppDbContext(testDbContext.Options); // fresh context, same connection
            (await verifyContext.NoteTags.AnyAsync(t => t.NoteId == note.Id, TestContext.Current.CancellationToken)).Should().BeTrue();
            testDbContext.Dispose();
            verifyContext.Dispose();
        }
        #endregion
    }

    /// <summary>
    /// A helper class for creating an in-memory SQLite database context for testing purposes. This class implements IDisposable to ensure proper cleanup of resources.
    /// </summary>
    struct TestDbContext : IDisposable
    {
        /// <summary>
        /// Gets the instance of the AppDbContext used for testing. This property is initialized in the constructor and is used to interact with the in-memory database.
        /// </summary>
        public AppDbContext Context { get; }
        /// <summary>
        /// Gets the underlying SQLite connection used for the in-memory database. This connection is opened in the constructor and closed in the Dispose method to ensure proper resource management.
        /// </summary>
        private readonly SqliteConnection _connection;
        /// <summary>
        /// Gets the options used to configure the AppDbContext. These options are set up in the constructor to use the in-memory SQLite database and are passed to the AppDbContext instance.
        /// </summary>
        public DbContextOptions<AppDbContext> Options { get; }
        /// <summary>
        /// Initializes a new instance of the TestDbContext struct. This constructor sets up an in-memory SQLite database connection, configures the DbContext options to use this connection, and creates an instance of AppDbContext. It also applies any pending migrations to ensure the database schema is up to date.
        /// </summary>
        public TestDbContext()
        {
            _connection = new SqliteConnection("DataSource=:memory:");
            _connection.Open();
            Options = new DbContextOptionsBuilder<AppDbContext>().UseSqlite(_connection).Options;
            Context = new AppDbContext(Options);
            Context.Database.Migrate();
        }
        /// <summary>
        /// Disposes of the resources used by the TestDbContext. This method closes the SQLite connection and disposes of the AppDbContext instance to free up any unmanaged resources. It is called automatically when the TestDbContext is no longer needed, ensuring proper cleanup of resources.
        /// </summary>
        public void Dispose()
        {
            Context.Dispose();
            _connection.Close();
        }
    }   
}
