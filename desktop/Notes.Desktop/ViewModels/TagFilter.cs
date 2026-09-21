using CommunityToolkit.Mvvm.ComponentModel;

namespace Notes.Desktop.ViewModels
{
    /// <summary>One chip in the tag filter row.</summary>
    public partial class TagFilter : ObservableObject
    {
        public required string Tag { get; init; }

        [ObservableProperty]
        public partial bool IsSelected { get; set; }
    }
}
