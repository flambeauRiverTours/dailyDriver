using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Text;
using FluentAssertions;

namespace Notes.Desktop.Tests.ViewModelTests
{
    public class MainViewModelTests
    {
        #region Tests
        [Fact()]
        public void MainViewModelTests_Constructor_InitializesButtonClickCountToZero()
        {
            // Arrange & Act
            var viewModel = new MainWindowViewModel();
            // Assert
            viewModel.ButtonClickCount.Should().BeEquivalentTo("0");
        }

        [Fact()]
        public void MainViewModelTests_ButtonClickCount_RaisesPropertyChanged()
        {
            // Arrange
            var viewModel = new MainWindowViewModel();
            bool propertyChangedRaised = false;
            viewModel.PropertyChanged += (sender, e) =>
            {
                if (e.PropertyName == nameof(viewModel.ButtonClickCount))
                {
                    propertyChangedRaised = true;
                }
            };
            // Act
            viewModel.ButtonClickCount = "5";
            // Assert
            propertyChangedRaised.Should().BeTrue();
        }

        [Fact()]
        public void MainViewModelTests_IncrementButtonClickCount_IncrementsCount()
        {
            // Arrange
            var viewModel = new MainWindowViewModel();
            // Act
            viewModel.IncrementButtonClickCountCommand.Execute(null);
            // Assert
            viewModel.ButtonClickCount.Should().BeEquivalentTo("1");
        }
        #endregion
    }
}
