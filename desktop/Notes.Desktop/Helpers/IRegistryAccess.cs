using System;
using System.Collections.Generic;
using System.Text;

namespace Notes.Desktop.Helpers
{
    public interface IRegistryAccess
    {
        public string GetRegistryString(string key, string defaultValue);
        public void SetRegistryString(string key, string value);
        public void DeleteRegistryKey(string key);
    }
}
