using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Notes.Desktop.EntityFramework;

namespace Notes.Desktop.Tests.Helpers
{
    /// <summary>
    /// An <see cref="IDbContextFactory{TContext}"/> over a real, in-memory SQLite database.
    ///
    /// **The real provider, not EF's <c>InMemory</c> one.** `InMemory` does not enforce relational
    /// constraints at all — no foreign keys, no cascade — so a test of derive-on-save against it
    /// would pass whatever the schema actually said. The connection is held open for the fixture's
    /// lifetime because closing a `:memory:` connection discards the database.
    ///
    /// Each <see cref="CreateDbContext"/> call returns a *fresh* context over the same connection,
    /// which is what lets a test write through one and verify through another — proving a row
    /// reached SQLite rather than merely sitting in a change tracker.
    /// </summary>
    internal sealed class InMemoryDbContextFactory : IDbContextFactory<AppDbContext>, IDisposable
    {
        private readonly SqliteConnection _connection;
        private readonly DbContextOptions<AppDbContext> _options;

        public InMemoryDbContextFactory()
        {
            _connection = new SqliteConnection("DataSource=:memory:");
            _connection.Open();

            _options = new DbContextOptionsBuilder<AppDbContext>()
                .UseSqlite(_connection)
                .Options;

            using var context = new AppDbContext(_options);
            context.Database.Migrate();
        }

        public AppDbContext CreateDbContext() => new(_options);

        public void Dispose() => _connection.Dispose();
    }
}
