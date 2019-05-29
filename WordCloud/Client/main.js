//https://github.com/elastic/bower-elasticsearch-js

document.addEventListener('DOMContentLoaded', function () {
    makeWordCloud("");
}, false);

function makeWordCloud(words) {
    restCall(words).then(json => {
        let cloud = document.getElementById("wordCloud");
        cloud.innerHTML = "";

        let holder = document.getElementById("wordHolder");

        let words = json.words;

        let max = words[0][1];

        function relativeDist(x, xref, a) { //https://math.stackexchange.com/questions/500723/how-to-compute-the-relative-difference-between-two-numbers
            const absDif = Math.abs(x - xref);

            return 100*( absDif / xref)*(1 - Math.exp(- (absDif/a)))
        }

        const filter = holder.innerHTML.split("&amp;");
        words = words.filter(tuple => {
            return !filter.includes(tuple[0])
        }).map(function (tuple) {
            return [tuple[0], 100 - relativeDist(max, tuple[1], 50)/*(tuple[1] / sum) * height*/]
        });

        /*
        const minRelDist = temp[temp.length-1][1];

        temp = temp.map(function (tuple) {

            return [tuple[0], 100 - relativeDist(100, tuple[1], minRelDist)/*(tuple[1] / sum) * height]
        })*/


        WordCloud(cloud, {list: words, wait: 0, click: function (item, dimension, event) {
                holder.innerHTML += item[0] + "&";
            }
        });

        let button = document.getElementById("button");
        button.onclick = function (e) {
            makeWordCloud(restCall(holder.innerHTML.substring(0, holder.innerHTML.length-1)))
        }
        //WordCloudCodepen(document.getElementById("wordCloudCodepen"), temp)
    });
}

async function restCall(words = "") {
    const response = await fetch('http://localhost:9000/words');
    const json =  await response.json();

    return json
}

//https://codepen.io/stevn/pen/JdwNgw

