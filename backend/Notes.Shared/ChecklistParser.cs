using System.Globalization;
using System.Text.RegularExpressions;
using Notes.Shared.Model;

namespace Notes.Shared
{
    public static class ChecklistParser
    {
        #region Parse
        /// <summary>
        /// Regex definition for checklist items
        /// </summary>
        private static Regex checklistLineRegex = new Regex(".*[*+-]\\s\\[[ xX]\\]\\s*");

        /// <summary>
        /// Parses a given set of text for checklist items
        /// </summary>
        /// <param name="input">Input text as string</param>
        /// <returns>List of identified checklist items</returns>
        public static List<ChecklistItem> ParseChecklist(string input)
        {
            string[] inputLines = input.Replace("\r", "").Split("\n");
            List<ChecklistItem> result = new List<ChecklistItem>();
            foreach (string line in inputLines)
            {
                if (IsChecklistItem(line))
                {
                    result.Add(ParseConfirmedChecklistItem(line));
                }
                else if (IsPlainProse(line))
                {
                    result.Add(GetPlainProseChecklistItem(line));
                }
            }
            return result;
        }

        /// <summary>
        /// Checks if a string is plain prose
        /// </summary>
        /// <param name="line">Line of text</param>
        /// <returns>True if it's plain prose, false otherwise</returns>
        private static bool IsPlainProse(string line)
        {
            return !string.IsNullOrWhiteSpace(line);
        }

        /// <summary>
        /// Creates a checklist item from a plain prose line
        /// </summary>
        /// <param name="line">Plain prose line of text</param>
        /// <returns>ChecklistItem data from input line</returns>
        private static ChecklistItem GetPlainProseChecklistItem(string line)
        {
            return new ChecklistItem() { @checked = false, text = GetBody(line.TrimStart(), false), due = GetDueDate(line) };
        }

        /// <summary>
        /// Checks whether an input line is a structured checklist
        /// </summary>
        /// <param name="inputLine">Input line to check</param>
        /// <returns>True if the line is a structured checklist item, otherwise false</returns>
        private static bool IsChecklistItem(string inputLine)
        {
            return checklistLineRegex.IsMatch(inputLine);
        }

        /// <summary>
        /// Parses a confirmed checklist item line into a structured data item
        /// </summary>
        /// <param name="checklistLine">Strucutred checklist item text</param>
        /// <returns>ChecklistItem object populated from the input string</returns>
        private static ChecklistItem ParseConfirmedChecklistItem(string checklistLine)
        {
            return new ChecklistItem()
            {
                text = GetBody(checklistLine, true),
                @checked = GetChecked(checklistLine),
                due = GetDueDate(checklistLine)
            };
        }

        /// <summary>
        /// Gets whether the checklist item is checked
        /// </summary>
        /// <param name="checklistLine">String to parse</param>
        /// <returns>True if the checklist item is checked, otherwise false</returns>
        private static bool GetChecked(string checklistLine)
        {
            return !char.IsWhiteSpace(checklistLine[checklistLine.IndexOf("[") + 1]);
        }

        /// <summary>
        /// Gets the body of the checklist item
        /// </summary>
        /// <param name="checklistLine">Checklist item text to parse</param>
        /// <param name="hasCheckbox">Whether the checklist item has a checkbox</param>
        /// <returns>The body text of the checklist item</returns>
        private static string GetBody(string checklistLine, bool hasCheckbox)
        {
            IEnumerable<Match> dueDates = GetDueDates(checklistLine);
            if (dueDates.Any())
            {
                checklistLine = checklistLine.Remove(checklistLine.LastIndexOf(dueDates.Last().Value), dueDates.Last().Value.Length);
            }
            return hasCheckbox ? checklistLine.Substring(checklistLine.IndexOf("]") + 1).TrimStart() : checklistLine.Trim();
        }

        /// <summary>
        /// Gets the due date of the checklist item if it exists
        /// </summary>
        /// <param name="checklistLine">Checklist item text to parse</param>
        /// <returns>The due date of the checklist item, or null if it doesn't have one</returns>
        private static DateOnly? GetDueDate(string checklistLine)
        {
            var dueDates = GetDueDates(checklistLine);
            return dueDates.Any() ? DateOnly.ParseExact(dueDates.Last().Groups[1].Value, "yyyy-MM-dd", CultureInfo.InvariantCulture) : null;
        }

        /// <summary>
        /// Gets the due dates of the checklist item if it exists
        /// </summary>
        /// <param name="checklistLine">Checklist item text to parse</param>
        /// <returns>The due dates of the checklist item, or an empty collection if it doesn't have any</returns>
        private static IEnumerable<Match> GetDueDates(string checklistLine)
        {
            return DueDateRegex.Matches(checklistLine).Where(match => DateOnly.TryParseExact(match.Groups[1].Value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out _));
        }

        /// <summary>
        /// Regex definition for due dates in checklist items
        /// </summary>
        private static readonly Regex DueDateRegex = new Regex("(?:^|\\s)@(\\d{4}-\\d{2}-\\d{2})(?=\\s|$)");
        #endregion

        #region Render
        /// <summary>
        /// Renders a checklist list
        /// </summary>
        /// <param name="checklist">List of checklist items</param>
        /// <returns>String representing the checklist</returns>
        public static string RenderChecklist(IEnumerable<ChecklistItem> checklist)
        {
            string result = "";
            foreach (ChecklistItem checklistItem in checklist)
            {
                if (!string.IsNullOrEmpty(result)) { result += "\n"; }
                if(string.IsNullOrEmpty(checklistItem.text))
                {
                    result += string.Format("- [{0}]", GetChecked(checklistItem));
                }
                else
                {
                    result += string.Format("- [{0}] {1}", GetChecked(checklistItem), checklistItem.text);
                }
                if (checklistItem.due.HasValue)
                {
                    result += string.Format(" @{0}", checklistItem.due.Value.ToString("yyyy-MM-dd"));
                }
            }
            return result;
        }

        /// <summary>
        /// Gets a string representation of whether the checklist item is checked
        /// </summary>
        /// <param name="checklistItem">Checklist item data object</param>
        /// <returns>"X" if checked, otherwise " "</returns>
        private static string GetChecked(ChecklistItem checklistItem)
        {
            return checklistItem.@checked ? "x" : " ";
        }
        #endregion

        #region Clean
        /// <summary>
        /// Cleans a checklist by removing empty items and trimming whitespace
        /// </summary>
        /// <param name="checklistItems">List of checklist items to clean</param>
        /// <returns>List of cleaned checklist items</returns>
        public static IEnumerable<ChecklistItem> Clean(IReadOnlyCollection<ChecklistItem> checklistItems)
        {
            return checklistItems.Where(item => !string.IsNullOrWhiteSpace(item.text) || item.due.HasValue).Select(item => new ChecklistItem { @checked = item.@checked, text = item.text.Trim(), due = item.due }).ToList();
        }
        #endregion

        #region Normalize
        /// <summary>
        /// Normalizes a checklist by moving unchecked items to the top and checked items to the bottom
        /// </summary>
        /// <param name="checklistItems">List of checklist items to normalize</param>
        /// <returns>List of normalized checklist items</returns>
        public static IEnumerable<ChecklistItem> Normalize(IReadOnlyCollection<ChecklistItem> checklistItems)
        {
            return checklistItems.OrderBy(item => item.@checked).ToList();
        }
        #endregion

        #region Convert
        /// <summary>
        /// Converts a string of text into a checklist representation
        /// </summary>
        /// <param name="input">String of text to convert</param>
        /// <returns>String representing the checklist</returns>
        public static string Convert(string input)
        {
            return RenderChecklist(ParseChecklist(input));
        }
        #endregion

        #region ToNoteBody
        /// <summary>
        /// Converts a list of checklist items into a single string suitable for use as a note body
        /// </summary>
        /// <param name="checklistItems">String of checklist items to convert</param>
        /// <returns>A single string representing the note body</returns>
        public static string ToNoteBody(string checklistItems)
        {
            return string.Join("\n", Clean(ParseChecklist(checklistItems)).Select(item => item.text));
        }
        #endregion
    }
}
