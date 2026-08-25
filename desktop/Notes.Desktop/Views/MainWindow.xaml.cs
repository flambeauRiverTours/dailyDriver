using System.Windows;
using Notes.Desktop.ViewModels;
namespace Notes.Desktop.Views
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        /// <summary>
        /// Initializes a new instance of the MainWindow class with the specified view model.
        /// </summary>
        /// <param name="viewModel">The view model for the main window.</param>
        public MainWindow(MainWindowViewModel viewModel)
        {
            InitializeComponent();
            DataContext = viewModel;
        }
    }
}
