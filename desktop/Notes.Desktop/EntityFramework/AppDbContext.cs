using System.IO;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;
using Notes.Desktop.EntityFramework.Model;
using Notes.Shared;

namespace Notes.Desktop.EntityFramework
{
    public class AppDbContext : DbContext
    {
        /// <summary>
        /// Initializes a new instance of the <see cref="AppDbContext"/> class with the specified options.
        /// </summary>
        /// <param name="options">The options for the context.</param>
        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }
        /// <summary>
        /// Gets or sets the DbSet for the Note entity.
        /// </summary>
        internal DbSet<Note> Notes { get; set; } = null!;
        /// <summary>
        /// Gets or sets the DbSet for the Task entity.
        /// </summary>
        internal DbSet<TaskRow> Tasks { get; set; } = null!;
        /// <summary>
        /// Gets or sets the DbSet for the NoteTag entity.
        /// </summary>
        internal DbSet<NoteTag> NoteTags { get; set; } = null!;
        /// <summary>
        /// Gets or sets the DbSet for the Outbox entity.
        /// </summary>
        internal DbSet<Outbox> Outboxes { get; set; } = null!;

        /// <summary>
        /// Migrate the database to the latest version asynchronously.
        /// </summary>
        internal Task MigrateAsync()
        {
            return Database.MigrateAsync();
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            BuildNotesTable(modelBuilder);
            BuildNoteTagsTable(modelBuilder);
            BuildTasksTable(modelBuilder);
            BuildOutboxTable(modelBuilder);
        }

        /// <summary>
        /// Builds the Notes table schema in the database using the provided ModelBuilder.
        /// </summary>
        /// <param name="modelBuilder">The ModelBuilder instance.</param>
        private void BuildNotesTable(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<Note>(entity =>
            {
                entity.ToTable("Notes");
                entity.HasKey(e => e.Id);
                entity.Property(e => e.Type).IsRequired();
                entity.Property(e => e.Title).IsRequired();
                entity.Property(e => e.Body).IsRequired();
                entity.Property(e => e.CreatedAt).IsRequired().HasConversion(UtcTicks);
                entity.Property(e => e.UpdatedAt).IsRequired().HasConversion(UtcTicks);
                entity.Property(e => e.Deleted).IsRequired();
                entity.Property(e => e.Rev);
                entity.Property(e => e.SourceDevice).IsRequired();

                entity.HasIndex(e => e.UpdatedAt);
                entity.HasIndex(e => e.Deleted);
            });
        }

        /// <summary>
        /// Builds the Tasks table schema in the database using the provided ModelBuilder.
        /// </summary>
        /// <param name="modelBuilder">The ModelBuilder instance.</param>
        private void BuildTasksTable(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<TaskRow>(entity =>
            {
                entity.ToTable("Tasks");
                entity.HasKey(e => new { e.NoteId, e.Position });
                entity.Property(e => e.NoteId).IsRequired();
                entity.Property(e => e.Position).IsRequired();
                entity.Property(e => e.Checked).IsRequired();
                entity.Property(e => e.Due);
                entity.Property(e => e.Text).IsRequired();

                entity.HasIndex(e => e.Due);
                entity.HasOne<Note>().WithMany().HasForeignKey(x => x.NoteId).OnDelete(DeleteBehavior.Cascade);
            });
        }

        /// <summary>
        /// Builds the NoteTags table schema in the database using the provided ModelBuilder.
        /// </summary>
        /// <param name="modelBuilder">The ModelBuilder instance.</param>
        private void BuildNoteTagsTable(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<NoteTag>(entity =>
            {
                entity.ToTable("NoteTags");
                entity.HasKey(e => new { e.NoteId, e.Tag });
                entity.Property(e => e.NoteId).IsRequired();
                entity.Property(e => e.Tag).IsRequired();

                entity.HasIndex(e => e.Tag);

                entity.HasOne<Note>().WithMany().HasForeignKey(x => x.NoteId).OnDelete(DeleteBehavior.Cascade);
            });
        }

        /// <summary>
        /// Builds the Outbox table schema in the database using the provided ModelBuilder.
        /// </summary>
        /// <param name="modelBuilder">The ModelBuilder instance.</param>
        private void BuildOutboxTable(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<Outbox>(entity =>
            {
                entity.ToTable("Outbox");
                entity.HasKey(e => e.NoteId);
                entity.Property(e => e.NoteId).IsRequired();
                entity.Property(e => e.BaseRev);
                entity.Property(e => e.QueuedAt).IsRequired().HasConversion(UtcTicks);

                entity.HasOne<Note>().WithMany().HasForeignKey(x => x.NoteId).OnDelete(DeleteBehavior.Cascade);
            });
        }

        /// <summary>
        /// Get the path to the SQLite database file.
        /// </summary>
        /// <returns>The path to the SQLite database file.</returns>
        internal static string GetDbPath()
        {
            var appDataFolder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var appFolder = Path.Combine(appDataFolder, "Notes.Desktop");
            Directory.CreateDirectory(appFolder); // Ensure the directory exists
            return Path.Combine(appFolder, "notes.db");
        }

        /// <summary>
        /// Saves the changes
        /// </summary>
        /// <param name="ct">Cancellation token</param>
        /// <returns>Task to await</returns>
        public override async Task<int> SaveChangesAsync(CancellationToken ct = default)
        {
            return await SaveChangesAsync(true, ct);
        }

        /// <summary>
        /// Updates derived values from a given note
        /// </summary>
        /// <param name="entity">Note entity object</param>
        /// <param name="ct">Cancellation token</param>
        /// <returns>Task to await</returns>
        private async Task DeriveFor(Note entity, CancellationToken ct)
        {
            NoteTags.RemoveRange(await NoteTags.Where(t => t.NoteId == entity.Id).ToListAsync(ct));
            NoteTags.AddRange(TagParser.ParseTags(entity.Body).Select(tag => new NoteTag { NoteId = entity.Id, Tag = tag }));

            Tasks.RemoveRange(await Tasks.Where(t => t.NoteId == entity.Id).ToListAsync(ct));
            Tasks.AddRange(TaskIndex.TasksOf(entity.Type, entity.Body, entity.Id)
                .Select(item => new TaskRow() { Checked = item.@checked, Due = item.due, Text = item.text, Position = item.position, NoteId = entity.Id }));

            if (await Outboxes.FindAsync([entity.Id], ct) is null)
            {
                Outboxes.Add(new Outbox { NoteId = entity.Id, BaseRev = entity.Rev, QueuedAt = DateTimeOffset.UtcNow });
            }

        }

        /// <summary>
        /// Don't use
        /// </summary>
        /// <returns></returns>
        /// <exception cref="InvalidOperationException"></exception>
        public override int SaveChanges()
        {
            throw new InvalidOperationException("Use the SaveChangesAsync(CancellationToken ct) method");
        }

        /// <summary>
        /// Don't use
        /// </summary>
        /// <param name="acceptAllChangesOnSuccess"></param>
        /// <returns></returns>
        public override int SaveChanges(bool acceptAllChangesOnSuccess) => SaveChanges();

        /// <summary>
        /// Saves the changes
        /// </summary>
        /// <param name="acceptAllChangesOnSuccess">Passed to base method</param>
        /// <param name="ct">Cancellation token</param>
        /// <returns>Task to await</returns>
        public override async Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken ct = default)
        {
            foreach (var entry in ChangeTracker.Entries<Note>()
                    .Where(entry => entry.State == EntityState.Added || entry.State == EntityState.Modified).ToList())
            {
                await DeriveFor(entry.Entity, ct);
            }
            return await base.SaveChangesAsync(acceptAllChangesOnSuccess, ct);
        }

        /// <summary>
        /// Stores timestamps as UTC ticks so SQLite can ORDER BY them — it refuses to order
        /// DateTimeOffset, which it keeps as offset-bearing TEXT that does not sort lexically
        /// </summary>
        private static readonly ValueConverter<DateTimeOffset, long> UtcTicks = new(v => v.UtcTicks, v => new DateTimeOffset(v, TimeSpan.Zero));
    }
}
