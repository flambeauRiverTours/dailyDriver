using System;
using System.Collections.Generic;
using System.Text;
using Notes.Shared.Model;

namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public class ChecklistCase
    {
        public required string Name { get; init; } = string.Empty;
        public required string Body { get; init;  } = string.Empty;
        public required IReadOnlyCollection<ChecklistItem> Items { get; init; }
    }
}
