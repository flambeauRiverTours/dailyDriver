using System.Text.Json;

namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public static class FixtureParser
    {
        private static readonly JsonSerializerOptions _jsonOptions = new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        };
        /// <summary>
        /// Parses a JSON fixture file into an object of type T.
        /// </summary>
        /// <typeparam name="T">Type of the object to deserialize into</typeparam>
        /// <param name="fileName">Name of the JSON fixture file</param>
        /// <returns>Deserialized object of type T</returns>
        /// <exception cref="InvalidOperationException">Thrown when deserialization fails</exception>
        public static T Parse<T>(string fileName)
        {
            var result = JsonSerializer.Deserialize<T>(GetJSON(fileName), _jsonOptions);
            if (result == null)
            {
                throw new InvalidOperationException($"Failed to deserialize fixture file '{fileName}' into type '{typeof(T).FullName}'.");
            }
            return result;
        }

        /// <summary>
        /// Gets the JSON content from a fixture file.
        /// </summary>
        /// <param name="fileName">Name of the JSON fixture file</param>
        /// <returns>JSON content as a string</returns>
        /// <exception cref="FileNotFoundException">Thrown when the fixture file is not found</exception>
        private static string GetJSON(string fileName)
        {
            string targetFileName = Path.Combine(AppContext.BaseDirectory, "fixtures", fileName);

            if (!File.Exists(targetFileName))
            {
                throw new FileNotFoundException("Fixture file not found", targetFileName);
            }

            return File.ReadAllText(targetFileName);
        }
    }
}
