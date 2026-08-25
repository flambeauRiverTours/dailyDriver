using System;
using System.Collections.Generic;
using System.Text;
using Microsoft.Win32;

namespace Notes.Desktop.Helpers
{
    public class RegistryAccessHelper: IRegistryAccess
    {
        /// <summary>
        /// Retrieves a string value from the Windows registry at the specified key under the BASIC_REG_PATH. If the key does not exist, it returns the provided default value.
        /// </summary>
        /// <param name="key">The registry key to retrieve.</param>
        /// <param name="defaultValue">The default value to return if the key does not exist.</param>
        /// <returns>The value of the registry key or the default value.</returns>
        public string GetRegistryString(string key, string defaultValue)
        {
            RegistryKey? regKey = Registry.CurrentUser.OpenSubKey(BASIC_REG_PATH);
            if (regKey == null)
            {
                return defaultValue;
            }
            else
            {
                return regKey.GetValue(key)?.ToString() ?? defaultValue;
            }
        }

        /// <summary>
        /// Sets a string value in the Windows registry at the specified key under the BASIC_REG_PATH. If the key does not exist, it will be created. The method returns the value that was set.
        /// </summary>
        /// <param name="key">The registry key to set.</param>
        /// <param name="value">The value to set.</param>
        public void SetRegistryString(string key, string value)
        {
            RegistryKey? regKey = Registry.CurrentUser.CreateSubKey(BASIC_REG_PATH, true);
            if (regKey != null)
            {
                regKey.SetValue(key, value);
            }
        }

        /// <summary>
        /// Deletes a registry key from the specified path in the Windows registry. If the key does not exist, no action is taken. The method returns the name of the deleted key.
        /// </summary>
        /// <param name="key">The registry key to delete.</param>
        public void DeleteRegistryKey(string key)
        {
            RegistryKey? regKey = Registry.CurrentUser.OpenSubKey(BASIC_REG_PATH, true);
            if (regKey != null)
            {
                regKey.DeleteValue(key, false);
            }
        }

        /// <summary>
        /// The base registry path for storing application settings. This path is used to create or open the registry key where the application settings are stored. The path is set to "SOFTWARE\DailyDriver\Notes.Desktop\" under the HKEY_CURRENT_USER hive.
        /// </summary>
        private const string BASIC_REG_PATH = @"SOFTWARE\DailyDriver\Notes.Desktop\";
    }
}
