using System.Globalization;
using System.Text.RegularExpressions;

namespace Notes.Shared
{
    public static class InputShortcutParser
    {
        /// <summary>
        /// Parses a string input for a date shortcut and returns the corresponding DateOnly value.
        /// </summary>
        /// <param name="input">The string input to parse.</param>
        /// <param name="today">The today's date.</param>
        /// <returns>The parsed DateOnly value or null if parsing fails.</returns>
        public static DateOnly? Parse(string input, DateOnly today)
        {
            var raw = input.Trim();
            if (DateOnly.TryParseExact(raw, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d))
            {
                return d;
            }
            raw = raw.ToLowerInvariant().Replace('-', ' ').CollapseWhitespace();
            var dateFromReservedWords = FindDateFromReservedWords(raw, today);
            if (dateFromReservedWords.HasValue)
            {
                return dateFromReservedWords.Value;
            }
            
            return GetDateFromShortcutRegex(raw, today);
        }

        /// <summary>
        /// Finds a date from reserved words like "today", "tomorrow", "next week", etc.
        /// </summary>
        /// <param name="input">The string input to parse.</param>
        /// <param name="today">The today's date.</param>
        /// <returns>The parsed DateOnly value or null if parsing fails.</returns>
        private static DateOnly? FindDateFromReservedWords(string input, DateOnly today)
        {
            return input switch
            {
                "today" => today,
                "tomorrow" => today.AddDays(1),
                "next week" => today.AddDays(7),
                "next month" => today.AddMonths(1),
                "next year" => today.AddYears(1),
                _ => null
            };
        }

        /// <summary>
        /// Regex to match date shortcuts in the format of "t+1", "w+2", "m+3", or "y+4".
        /// </summary>
        private static readonly Regex DateShortcutRegex = new Regex("^([twmy])\\+(\\d{1,4})$");

        /// <summary>
        /// Parses a string input for a date shortcut using regex and returns the corresponding DateOnly value.
        /// </summary>
        /// <param name="input">The string input to parse.</param>
        /// <param name="today">The today's date.</param>
        /// <returns>The parsed DateOnly value or null if parsing fails.</returns>
        private static DateOnly? GetDateFromShortcutRegex(string input, DateOnly today)
        {
            var matches = DateShortcutRegex.Matches(input);
            if (matches.Count > 0)
            {
                var match = matches.Last();
                var unit = match.Groups[1].Value;
                var value = int.Parse(match.Groups[2].Value);
                return unit switch
                {
                    "t" => today.AddDays(value),
                    "w" => today.AddDays(value * 7),
                    "m" => today.AddMonths(value),
                    "y" => today.AddYears(value),
                    _ => null
                };
            }
            return null;
        }

        /// <summary>
        /// Collapses multiple whitespace characters in a string into a single space.
        /// </summary>
        /// <param name="input">The string input to collapse.</param>
        /// <returns>The string with collapsed whitespace.</returns>
        private static string CollapseWhitespace(this string input)
        {
            return Regex.Replace(input, @"\s+", " ");
        }
    }
}
