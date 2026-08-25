using System.Configuration;
using System.Data;
using System.IO;
using System.Windows;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Notes.Desktop.EntityFramework;

namespace Notes.Desktop
{
    /// <summary>
    /// Interaction logic for App.xaml
    /// </summary>
    public partial class App : Application
    {
        #region Lifecycle
        /// <summary>
        /// Override the OnStartup method to configure and start the host, register services, and show the main window.
        /// </summary>
        /// <param name="e">The startup event arguments.</param>
        protected override async void OnStartup(StartupEventArgs e)
        {
            //prep builder
            HostApplicationBuilder builder = Host.CreateApplicationBuilder();
            builder.Logging.AddDebug();
            AddServices(builder.Services);

            //Build and start
            InternalHost = builder.Build();
            await InternalHost.StartAsync();

            //Migrate the database
            using var dbContext = InternalHost.Services
                .GetRequiredService<IDbContextFactory<AppDbContext>>()
                .CreateDbContext();
            await dbContext.MigrateAsync();

            //show window
            ShowMainWindow(InternalHost);
        }

        /// <summary>
        /// Override the OnExit method to stop and dispose of the host when the application exits.
        /// </summary>
        /// <param name="e">The exit event arguments.</param>
        protected override async void OnExit(ExitEventArgs e)
        {
            if (InternalHost != null)
            {
                await InternalHost.StopAsync();
                InternalHost.Dispose();
            }
            base.OnExit(e);
        }
        #endregion

        #region Properties
        /// <summary>
        /// Gets or sets the internal host instance used for dependency injection and application lifecycle management.
        /// </summary>
        private IHost? InternalHost { get; set; } = null;
        #endregion

        #region Helper Functions
        /// <summary>
        /// Registers application services with the dependency injection container.
        /// </summary>
        /// <param name="services">The service collection.</param>
        private void AddServices(IServiceCollection services)
        {
            services.AddSingleton<MainWindow>();
            services.AddSingleton<MainWindowViewModel>();
            services.AddDbContextFactory<AppDbContext>(options =>
                options.UseSqlite($"Data Source={AppDbContext.GetDbPath()}"));
        }

        /// <summary>
        /// Shows the main window of the application by resolving it from the service provider.
        /// </summary>
        /// <param name="_host">The host instance.</param>
        private void ShowMainWindow(IHost _host)
        {
            var mainWindow = _host.Services.GetRequiredService<MainWindow>();
            mainWindow.Show();
        }
        #endregion
    }

}
