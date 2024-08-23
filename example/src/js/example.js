import { NAIFilterGameUrl } from 'matrix-filter-game-url';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    NAIFilterGameUrl.echo({ value: inputValue })
}
