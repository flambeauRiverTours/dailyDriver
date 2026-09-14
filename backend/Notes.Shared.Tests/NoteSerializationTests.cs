using System.Text.Json;
using FluentAssertions;
using Notes.Shared.Model;

namespace Notes.Shared.Tests
{
    public class NoteSerializationTests
    {
        private JsonSerializerOptions _jsonSerializerOptions = new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        };
        [Fact]
        public void NoteSerialization_Deserialization_RoundTrip_ShouldBeEqual()
        {
            // Arrange
            var originalNote = new Note
            {
                id = Guid.NewGuid(),
                userId = "user123",
                type = NoteType.Note,
                title = "Sample Note",
                body = "This is a sample note body.",
                tags = new List<string> { "tag1", "tag2" },
                createdAt = DateTimeOffset.UtcNow,
                updatedAt = DateTimeOffset.UtcNow,
                deleted = false,
                sourceDevice = "device123",
                rev = 1
            };
            // Act
            var jsonString = JsonSerializer.Serialize(originalNote, typeof(Note), _jsonSerializerOptions);
            var deserializedNote = JsonSerializer.Deserialize<Note>(jsonString, _jsonSerializerOptions);
            // Assert
            deserializedNote.Should().BeEquivalentTo(originalNote);
        }

        [Fact]
        public void NoteSerialization_ShouldHandleNullRev()
        {
            // Arrange
            var originalNote = new Note
            {
                id = Guid.NewGuid(),
                userId = "user123",
                type = Notes.Shared.Model.NoteType.Note,
                title = "Sample Note",
                body = "This is a sample note body.",
                tags = new List<string> { "tag1", "tag2" },
                createdAt = DateTimeOffset.UtcNow,
                updatedAt = DateTimeOffset.UtcNow,
                deleted = false,
                sourceDevice = "device123",
                rev = null // Testing null Rev
            };
            // Act
            var jsonString = JsonSerializer.Serialize(originalNote, typeof(Note), _jsonSerializerOptions);
            var deserializedNote = JsonSerializer.Deserialize<Note>(jsonString, _jsonSerializerOptions);
            // Assert
            deserializedNote.Should().BeEquivalentTo(originalNote);
        }

        [Fact]
        public void NoteSerialization_ShouldHandleEmptyTags()
        {
            // Arrange
            var originalNote = new Note
            {
                id = Guid.NewGuid(),
                userId = "user123",
                type = NoteType.Note,
                title = "Sample Note",
                body = "This is a sample note body.",
                tags = new List<string>(), // Testing empty tags
                createdAt = DateTimeOffset.UtcNow,
                updatedAt = DateTimeOffset.UtcNow,
                deleted = false,
                sourceDevice = "device123",
                rev = 1
            };
            // Act
            var jsonString = JsonSerializer.Serialize(originalNote);
            var deserializedNote = JsonSerializer.Deserialize<Note>(jsonString);
            // Assert
            deserializedNote.Should().BeEquivalentTo(originalNote);
        }

        [Fact]
        public void NoteSerialization_HandlesJsonEnumAppropriatelyIntoDoc()
        {
            var originalNote = new Note
            {
                id = Guid.NewGuid(),
                userId = "user123",
                type = NoteType.Checklist,
                title = "Sample Note",
                body = "This is a sample note body.",
                tags = new List<string>(), // Testing empty tags
                createdAt = DateTimeOffset.UtcNow,
                updatedAt = DateTimeOffset.UtcNow,
                deleted = false,
                sourceDevice = "device123",
                rev = 1
            };
            var json = JsonSerializer.Serialize(originalNote, typeof(Note), _jsonSerializerOptions);
            using var doc = JsonDocument.Parse(json);
            doc.RootElement.GetProperty("type").GetString().Should().Be("checklist");
        }
        [Fact]
        public void NoteSerialization_HandlesJsonEnumAppropriatelyFromDoc()
        {
            var result = JsonSerializer.Deserialize<Note>("""{"type":"checklist", "id":"00000000-0000-0000-0000-000000000000", "userId":"user123", "title":"Sample Note", "body":"This is a sample note body.", "tags":[], "createdAt":"0001-01-01T00:00:00Z", "sourceDevice":"device123", "rev":1, "deleted":false}""", _jsonSerializerOptions);
            if(result == null)
            {
                throw new InvalidOperationException("Deserialization returned null");
            }
            result.type.Should().Be(NoteType.Checklist);
        }
    }
}
