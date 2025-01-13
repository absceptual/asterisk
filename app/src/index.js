import * as monaco from "monaco-editor"

// Select the container where the editor will be mounted
const container = document.getElementById('container');

// Initialize the Monaco editor
const editor = monaco.editor.create(container, {
    value: `function helloWorld() {\n    console.log('Hello, world!');\n}`,
    language: 'javascript',
    theme: 'vs-dark', // or 'vs-light'
    automaticLayout: true, // Automatically adjusts the layout
});

console.log('Monaco Editor initialized:', editor);