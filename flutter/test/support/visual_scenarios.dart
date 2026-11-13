import 'package:flutter/widgets.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/ui/settings/appearance_page.dart';
import 'package:maeve_flutter/ui/settings/general_pages.dart';
import 'package:maeve_flutter/ui/settings/about_pages.dart';
import 'package:maeve_flutter/ui/settings/licenses_page.dart';
import 'package:maeve_flutter/ui/settings/model_settings_page.dart';
import 'package:maeve_flutter/ui/settings/system_access_page.dart';
import 'package:maeve_flutter/ui/settings/amoo_page.dart';
import 'package:maeve_flutter/ui/settings/execution_settings_page.dart';
import 'package:maeve_flutter/ui/settings/run_history_page.dart';
import 'package:maeve_flutter/ui/settings/welcome_page.dart';

const visualPages = <String, Widget>{
  'settings': SettingsHubPage(),
  'models': ModelSettingsPage(),
  'add-model': AddModelPage(),
  'access': SystemAccessPage(),
  'amoo': AmooPage(),
  'execution': ExecutionSettingsPage(),
  'runs': RunHistoryPage(),
  'appearance': AppearancePage(),
  'performance': PerformancePage(),
  'about': AboutPage(),
  'licenses': LicensesPage(),
  'welcome': WelcomePage(),
};
