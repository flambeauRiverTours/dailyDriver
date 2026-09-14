using Microsoft.EntityFrameworkCore;
using Notes.Desktop.EntityFramework;

namespace Notes.Desktop.Tests.Helpers
{
    /// <summary>
    /// A do-nothing <see cref="IDbContextFactory{TContext}"/> for tests whose subject is not the
    /// database — composition of <c>MainWindowViewModel</c>, for instance.
    ///
    /// **Hand-written rather than a Moq mock, and that is not a style preference.** Castle
    /// DynamicProxy cannot generate a proxy for <c>IDbContextFactory&lt;AppDbContext&gt;</c> because
    /// <see cref="AppDbContext"/> is <c>internal</c> — the closed generic is effectively internal, and
    /// the dynamic proxy assembly has no access to it. Moq fails with
    /// <c>"Can not create proxy for type"</c>.
    ///
    /// The usual fix is an <c>InternalsVisibleTo("DynamicProxyGenAssembly2")</c> on the production
    /// assembly, which is a testability change to production code and off-limits here. This test
    /// assembly already sees <c>Notes.Desktop</c>'s internals through the existing
    /// <c>InternalsVisibleTo</c>, so a stub compiled *here* works where a runtime-generated one
    /// cannot.
    /// </summary>
    internal sealed class StubDbContextFactory : IDbContextFactory<AppDbContext>
    {
        public AppDbContext CreateDbContext() =>
            throw new NotSupportedException(
                "This test should not reach the database. If it does, give it a real context.");
    }
}
