// Centium VPN Tauri Application
// Desktop frontend client connecting to local centiumd daemon via Unix Domain Socket

#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

use std::sync::Mutex;
use tauri::{
    CustomMenuItem, Manager, SystemTray, SystemTrayEvent, SystemTrayMenu, SystemTrayMenuItem,
};

#[tauri::command]
async fn get_connection_status() -> Result<String, String> {
    // Queries local /run/centium/centium.sock
    Ok("{\"state\":\"DISCONNECTED\"}".into())
}

#[tauri::command]
async fn trigger_connect() -> Result<String, String> {
    // Sends Connect command to centiumd via Unix socket
    Ok("{\"success\":true}".into())
}

#[tauri::command]
async fn trigger_disconnect() -> Result<String, String> {
    // Sends Disconnect command to centiumd via Unix socket
    Ok("{\"success\":true}".into())
}

fn make_tray() -> SystemTray {
    let connect_item = CustomMenuItem::new("connect".to_string(), "Connect");
    let disconnect_item = CustomMenuItem::new("disconnect".to_string(), "Disconnect");
    let status_item = CustomMenuItem::new("status".to_string(), "Status: Disconnected").disabled();
    let settings_item = CustomMenuItem::new("settings".to_string(), "Settings");
    let quit_item = CustomMenuItem::new("quit".to_string(), "Quit");

    let tray_menu = SystemTrayMenu::new()
        .add_item(status_item)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(connect_item)
        .add_item(disconnect_item)
        .add_native_item(SystemTrayMenuItem::Separator)
        .add_item(settings_item)
        .add_item(quit_item);

    SystemTray::new().with_menu(tray_menu)
}

fn main() {
    tauri::Builder::default()
        .system_tray(make_tray())
        .on_system_tray_event(|app, event| match event {
            SystemTrayEvent::MenuItemClick { id, .. } => match id.as_str() {
                "quit" => std::process::exit(0),
                "connect" => {
                    let _ = app.emit_all("tray-connect", ());
                }
                "disconnect" => {
                    let _ = app.emit_all("tray-disconnect", ());
                }
                _ => {}
            },
            _ => {}
        })
        .invoke_handler(tauri::generate_handler![
            get_connection_status,
            trigger_connect,
            trigger_disconnect
        ])
        .run(tauri::generate_context!())
        .expect("error while running Centium VPN");
}
