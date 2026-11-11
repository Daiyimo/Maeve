import 'package:maeve_flutter/ui/settings/licenses_page.dart';
import 'package:flutter/widgets.dart';
import 'package:maeve_flutter/app/onboarding.dart';
import 'package:maeve_flutter/ui/settings/settings_hub.dart';
import 'package:maeve_flutter/ui/settings/appearance_page.dart';
import 'package:maeve_flutter/ui/settings/general_pages.dart';
import 'package:maeve_flutter/ui/settings/about_pages.dart';
import 'package:maeve_flutter/ui/phone/phone_control_page.dart';
import 'package:maeve_flutter/ui/runs/runs_page.dart';
import 'package:maeve_flutter/ui/memory/memory_page.dart';
import 'package:maeve_flutter/ui/more/more_pages.dart';

/// Every navigable Flutter page, shared by layout checks and local visual review.
const visualPages = <String, Widget>{
  'licenses': LicensesPage(),
  'settings': SettingsHubPage(),
  'appearance': AppearancePage(),
  'interface': InterfacePage(),
  'performance': PerformancePage(),
  'voice': VoicePage(),
  'notifications': NotificationPage(),
  'reply': ReplyPrefsPage(),
  'privacy': PrivacyPage(),
  'access': SystemAccessPage(),
  'background': BackgroundGuidePage(),
  'diagnostics': DiagnosticsPage(),
  'language': LanguagePage(),
  'account': AccountPage(),
  'engine': EnginePage(),
  'providers': ProvidersPage(),
  'plugins': PluginsPage(),
  'remote': RemotePage(),
  'update': UpdatePage(),
  'about': AboutPage(),
  'phone': PhoneControlPage(),
  'runs': RunsPage(),
  'memory': MemoryPage(),
  'web': WebViewPage(),
  'terminal': TerminalPage(),
  'community': CommunityPage(),
  'onboarding': OnboardingPage(),
};
