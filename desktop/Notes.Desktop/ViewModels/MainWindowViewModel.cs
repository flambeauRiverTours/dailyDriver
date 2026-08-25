using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Notes.Desktop
{
    public partial class MainWindowViewModel: ObservableObject
    {
        public MainWindowViewModel()
        {
            buttonClickInt = 0;
        }

        [ObservableProperty]
        public partial string ButtonClickCount { get; set; }

        private int buttonClickInt { get; set {
                field = value;
                ButtonClickCount = buttonClickInt.ToString();
            } }
        [RelayCommand]
        internal void IncrementButtonClickCount()
        {
            buttonClickInt++;
        }
    }
}
