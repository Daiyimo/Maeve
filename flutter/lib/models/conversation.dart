class Conversation {
  Conversation({required this.id, required this.title, DateTime? updatedAt})
    : updatedAt = updatedAt ?? DateTime.now();

  final String id;
  String title;
  DateTime updatedAt;
  final List<Message> messages = [];
}

class Message {
  Message(this.text, {required this.isUser});
  final String text;
  final bool isUser;
}
