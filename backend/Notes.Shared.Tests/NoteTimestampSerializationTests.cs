using System.Text.Json;
using FluentAssertions;
using Notes.Shared.Model;

namespace Notes.Shared.Tests
{
    /// <summary>
    /// The wire format of timestamps.
    ///
    /// Serialized with <b>no options</b> on purpose. Production paths each bring their own
    /// serializer — the desktop's <c>HttpClient</c>, the Functions runtime, the Cosmos SDK — and
    /// none of them will use an options object configured inside a test. Asserting against
    /// <see cref="JsonSerializer.Serialize{TValue}(TValue, JsonSerializerOptions)"/> with a
    /// hand-built options instance would test the test.
    ///
    /// Nothing pins this across languages: the three fixture corpora cover body *syntax* only, not
    /// the document envelope. Property names, enum wire values and date format have no shared
    /// corpus, so these assertions guard the C# half and Android's agreement is still by inspection.
    ///
    /// The format itself is settled in <c>docs/design.md</c> §6.2: ISO 8601, explicit numeric
    /// offset, <b>whole seconds</b>, produced in UTC by convention. No converter enforces any of
    /// that — it holds because System.Text.Json's native <see cref="DateTimeOffset"/> shape already
    /// matches, and because producers use <c>UtcNow</c> and truncate. A converter was considered
    /// and rejected as disproportionate; if the convention slips, that is the fix.
    /// </summary>
    public class NoteTimestampSerializationTests
    {
        private static Note NoteAt(DateTimeOffset created) => new()
        {
            id = Guid.Parse("11111111-1111-1111-1111-111111111111"),
            userId = "user123",
            type = NoteType.Note,
            title = "Standup notes",
            body = "markdown source",
            tags = ["work"],
            createdAt = created,
            updatedAt = created,
            deleted = false,
            sourceDevice = "desktop",
            rev = 7,
        };

        /// <summary>
        /// §6.2 documents ISO 8601 with an explicit numeric offset and whole seconds — no
        /// fractional part. This is System.Text.Json's native shape for
        /// <see cref="DateTimeOffset"/>, so it holds with no converter.
        /// </summary>
        [Fact]
        public void CreatedAt_SerializesAsIso8601WithExplicitOffsetAndWholeSeconds()
        {
            var note = NoteAt(new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero));

            var json = JsonSerializer.Serialize(note);

            using var doc = JsonDocument.Parse(json);
            doc.RootElement.GetProperty("createdAt").GetString()
                .Should().Be("2026-08-13T09:00:00+00:00");
        }

        /// <summary>
        /// §6.2 does not require the offset to be zero, so a producer in a non-UTC zone writes a
        /// different *string* for the same moment. What the format does guarantee is that the
        /// **instant** survives, which is the property worth pinning — asserting the offset is
        /// preserved verbatim would only restate what the serializer happens to do.
        ///
        /// (Producing in UTC is a convention — see §6.2 — not something enforced here. Nothing
        /// orders by timestamps; §7.1 gives sync a clock-independent ordering via server-assigned
        /// <c>rev</c> precisely so this cannot matter.)
        /// </summary>
        [Fact]
        public void CreatedAt_RoundTripsTheSameInstantRegardlessOfOffset()
        {
            var berlin = new DateTimeOffset(2026, 8, 13, 11, 0, 0, TimeSpan.FromHours(2));
            var utc = new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero);

            var fromBerlin = JsonSerializer.Deserialize<Note>(JsonSerializer.Serialize(NoteAt(berlin)))!;

            fromBerlin.createdAt.ToUniversalTime().Should().Be(utc);
            fromBerlin.createdAt.Should().Be(utc, "DateTimeOffset equality compares the instant");
        }

        /// <summary>
        /// Read is deliberately liberal: both the <c>Z</c> suffix and an explicit <c>+00:00</c>
        /// denote UTC, and a client written against either spelling must be readable.
        /// </summary>
        [Theory]
        [InlineData("2026-08-13T09:00:00Z")]
        [InlineData("2026-08-13T09:00:00+00:00")]
        [InlineData("2026-08-13T11:00:00+02:00")]
        public void CreatedAt_DeserializesAnyIso8601Spelling(string createdAt)
        {
            var json = $$"""
                {"id":"11111111-1111-1111-1111-111111111111","userId":"user123","type":"note",
                 "title":"Standup notes","body":"markdown source","tags":["work"],
                 "createdAt":"{{createdAt}}","updatedAt":"2026-08-13T09:42:11+00:00",
                 "deleted":false,"sourceDevice":"desktop","rev":7}
                """;

            JsonSerializer.Deserialize<Note>(json)!.createdAt
                .Should().Be(new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero));
        }

        /// <summary>Reading back what §6.2 documents must give the same instant.</summary>
        [Fact]
        public void CreatedAt_DeserializesFromTheDocumentedForm()
        {
            const string json = """
                {"id":"11111111-1111-1111-1111-111111111111","userId":"user123","type":"note",
                 "title":"Standup notes","body":"markdown source","tags":["work"],
                 "createdAt":"2026-08-13T09:00:00Z","updatedAt":"2026-08-13T09:42:11Z",
                 "deleted":false,"sourceDevice":"desktop","rev":7}
                """;

            var note = JsonSerializer.Deserialize<Note>(json);

            note!.createdAt.Should().Be(new DateTimeOffset(2026, 8, 13, 9, 0, 0, TimeSpan.Zero));
        }
    }
}
