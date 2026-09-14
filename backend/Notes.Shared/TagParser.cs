using System.Text.RegularExpressions;

namespace Notes.Shared
{
    /// <summary>
    /// Provides functionality to parse tags from a given input string, excluding those found within fenced code blocks.
    /// </summary>
    public static class TagParser
    {
        /// <summary>
        /// Parses the input string and extracts unique tags that are not within fenced code blocks.
        /// </summary>
        /// <param name="input">The input string to parse.</param>
        /// <returns>A list of unique tags.</returns>
        public static List<string> ParseTags(string input)
        {
            bool inFence = false;
            List<string> tags = new List<string>();
            foreach (string line in input.Split('\n'))
            {
                if (IsFenceLine(line))
                {
                    inFence = !inFence;
                    continue;
                }
                if (!inFence)
                {
                    MatchTagsIn(line, tags);
                }
            }
            return tags;
        }

        private static readonly Regex TagRegex = new Regex(@"#[\p{L}\p{N}_-]+");
        private static readonly Regex FenceRegex = new Regex(@"^[ ]{0,3}(`{3,}|~{3,}).*?(?:^[ ]{0,3}\1.*?$|\z)");

        /// <summary>
        /// Determines if a line is a fenced code block delimiter.
        /// </summary>
        /// <param name="line">The line to check.</param>
        /// <returns>true if the line is a fenced code block delimiter; otherwise, false.</returns>
        private static bool IsFenceLine(string line)
        {
            return FenceRegex.IsMatch(line);
        }

        /// <summary>
        /// Matches tags in a line and adds them to the provided list if they are valid and not already present.
        /// </summary>
        /// <param name="line">The line to search for tags.</param>
        /// <param name="tags">The list to add matching tags to.</param>
        private static void MatchTagsIn(string line, List<string> tags)
        {
            List<Match> tagMatches = TagRegex.Matches(line).ToList();
            foreach (Match tag in tagMatches)
            {
            }
            tagMatches.ForEach(match =>
            {
                string tag = match.Value.Substring(1).ToLowerInvariant(); // Remove the '#' character
                int index = line.IndexOf(match.Value);
                if (IsPrecededByNonWordCharacter(line, index) && ContainsLetter(tag))
                {
                    if (!tags.Contains(tag))
                    {
                        tags.Add(tag);
                    }
                }
            });
        }

        /// <summary>
        /// Determines if the character preceding the tag is a non-word character (e.g., whitespace or start of string).
        /// </summary>
        /// <param name="input">The input string.</param>
        /// <param name="index">The index of the tag.</param>
        /// <returns>true if the character preceding the tag is a non-word character; otherwise, false.</returns>
        private static bool IsPrecededByNonWordCharacter(string input, int index)
        {
            if (index == 0) return true; // Start of string
            return char.IsWhiteSpace(input[index - 1]);
        }

        /// <summary>
        /// Determines if the input string contains at least one letter.
        /// </summary>
        /// <param name="input">The input string.</param>
        /// <returns>true if the string contains at least one letter; otherwise, false.</returns>
        private static bool ContainsLetter(string input)
        {
            if (input == null) return false;
            foreach (char c in input)
            {
                if (char.IsLetter(c))
                {
                    return true;
                }
            }
            return false;
        }
    }
}
