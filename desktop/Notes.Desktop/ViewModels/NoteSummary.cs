using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace Notes.Desktop.ViewModels
{

    /// <summary>One row in the note list. A projection for display, not an entity.</summary>
    public sealed class NoteSummary
    {
        public required Guid Id { get; init; }
        public required string Title { get; init; }
        public required string Snippet { get; init; }
        public required string UpdatedDisplay { get; init; }


        /// <summary>
        /// Formats a DateTimeOffset appropriately for use in UpdatedDisplay
        /// </summary>
        /// <param name="updatedAt">DateTimeOffset to format</param>
        /// <returns>String formatted with datetimeoffset info</returns>
        public static string ToUpdatedDisplay(DateTimeOffset updatedAt)
        {
            var local = updatedAt.ToLocalTime();
            if (local.Date == DateTimeOffset.Now.Date)
            {
                return local.ToString("HH:mm", CultureInfo.InvariantCulture);
            }

            if (local.Year == DateTimeOffset.Now.Year)
            {
                return local.ToString("d MMM", CultureInfo.InvariantCulture);
            }

            return local.ToString("d MMM yyyy", CultureInfo.InvariantCulture);
        }
    }
}
