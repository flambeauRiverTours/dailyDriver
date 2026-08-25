using Microsoft.Win32;
using Notes.Desktop.Helpers;

namespace Notes.Desktop.Tests.Helpers
{
    /// <summary>
    /// Unit tests for the RegistryAccessHelper class.
    /// </summary>
    public class RegistryAccessHelperTests
    {
        #region Tests
        [Fact()]
        public void RegistryAccessHelper_GetRegistryString_ReturnsDefaultValue_WhenKeyDoesNotExist()
        {
            // Arrange
            var registryAccessHelper = new RegistryAccessHelper();
            string randomKey = GetRandomRegKey();
            string defaultValue = "DefaultValue";
            // Act
            string result = registryAccessHelper.GetRegistryString(randomKey, defaultValue);
            // Assert
            Assert.Equal(defaultValue, result);

            registryAccessHelper.DeleteRegistryKey(randomKey);
        }

        [Fact()]
        public void RegistryAccessHelper_GetRegistryString_ReturnsValue_WhenKeyExists()
        {
            // Arrange
            var registryAccessHelper = new RegistryAccessHelper();
            string randomKey = GetRandomRegKey();
            string expectedValue = "TestValue";
            registryAccessHelper.SetRegistryString(randomKey, expectedValue);
            // Act
            string result = registryAccessHelper.GetRegistryString(randomKey, "DefaultValue");
            // Assert
            Assert.Equal(expectedValue, result);
            registryAccessHelper.DeleteRegistryKey(randomKey);
        }

        [Fact()]
        public void RegistryAccessHelper_SetRegistryString_SetsValueSuccessfullyIfKeyDoesNotExist()
        {
            // Arrange
            var registryAccessHelper = new RegistryAccessHelper();
            string randomKey = GetRandomRegKey();
            string expectedValue = "TestValue";
            // Act
            registryAccessHelper.SetRegistryString(randomKey, expectedValue);
            string result = registryAccessHelper.GetRegistryString(randomKey, "DefaultValue");
            // Assert
            Assert.Equal(expectedValue, result);
            registryAccessHelper.DeleteRegistryKey(randomKey);
        }

        [Fact()]
        public void RegistryAccessHelper_SetRegistryString_SetsValueSuccessfullyIfKeyDoesExist()
        {
            // Arrange
            var registryAccessHelper = new RegistryAccessHelper();
            string randomKey = GetRandomRegKey();
            registryAccessHelper.SetRegistryString(randomKey, "ADifferentValue");
            string expectedValue = "TestValue";
            // Act
            registryAccessHelper.SetRegistryString(randomKey, expectedValue);
            string result = registryAccessHelper.GetRegistryString(randomKey, "DefaultValue");
            // Assert
            Assert.Equal(expectedValue, result);
            registryAccessHelper.DeleteRegistryKey(randomKey);
        }

        [Fact()]
        public void RegistryAccessHelper_DeleteRegistryKey_DeletesKeySuccessfully()
        {
            // Arrange
            var registryAccessHelper = new RegistryAccessHelper();
            string randomKey = GetRandomRegKey();
            registryAccessHelper.SetRegistryString(randomKey, "TestValue");
            // Act
            registryAccessHelper.DeleteRegistryKey(randomKey);
            string result = registryAccessHelper.GetRegistryString(randomKey, "DefaultValue");
            // Assert
            Assert.Equal("DefaultValue", result);
        }
        #endregion

        #region Helpers
        /// <summary>
        /// Generates a random registry key name for testing purposes.
        /// </summary>
        /// <returns></returns>
        private string GetRandomRegKey()
        {
            // Generate a random registry key name for testing
            return "TestKey_" + Guid.NewGuid().ToString();
        }
        #endregion
    }
}
