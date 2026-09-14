using System;
using System.Collections.Generic;
using System.Text;

namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public class ChecklistFixture
    {
        private static readonly Lazy<ChecklistFixture> _instance =
        new(() => FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json"));

        public static ChecklistFixture Instance => _instance.Value;
        /// <summary>
        /// Gets or sets the list of tag cases for testing.
        /// </summary>
        public required IReadOnlyList<ChecklistCase> Cases { get; init; }
        /// <summary>
        /// Gets or sets the list of render cases for testing.
        /// </summary>
        public required IReadOnlyList<ChecklistCase> RenderCases { get; init; }
        /// <summary>
        /// Gets or sets the list of conversion cases for testing.
        /// </summary>
        public required IReadOnlyList<ChecklistConversionCase> ConversionCases { get; init; }
        /// <summary>
        /// Gets or sets the list of normalize cases for testing.
        /// </summary>
        public required IReadOnlyList<ChecklistNormalizeCase> NormalizeCases { get; init; }
        /// <summary>
        /// Gets or sets the list of cleaning cases for testing.
        /// </summary>
        public required IReadOnlyList<ChecklistCleanCase> CleanCases { get; init; }
        
    }
}
